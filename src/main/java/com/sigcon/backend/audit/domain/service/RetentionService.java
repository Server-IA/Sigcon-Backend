package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.domain.model.AuditLog;
import com.sigcon.backend.audit.domain.model.AuditPurgeRecord;
import com.sigcon.backend.audit.domain.model.AuditRetentionPolicy;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.repository.AuditLogRepository;
import com.sigcon.backend.audit.domain.repository.AuditPurgeRecordRepository;
import com.sigcon.backend.audit.domain.repository.AuditRetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HU-AU-10: Servicio de politicas de retencion + legal hold + purga programada.
 *
 * <p>Funciones:
 * <ul>
 *   <li>{@link #calculateRetentionUntil}: dada (modulo, severidad), calcula la
 *       fecha hasta la cual se debe conservar el log. Lo usa AuditLogService
 *       al insertar nuevos registros.</li>
 *   <li>{@link #setLegalHold}: marca un log con legal hold (HU-AU-10 E7).</li>
 *   <li>{@link #releaseLegalHold}: libera el legal hold.</li>
 *   <li>{@link #runPurgeScheduled}: scheduler @Scheduled diario a las 2:30 AM.
 *       Busca logs con retencion vencida + sin legal hold + sin archived_at,
 *       calcula hash del lote, marca como purgados (archived_at + archived_hash)
 *       y crea AuditPurgeRecord.</li>
 *   <li>CRUD de politicas para que el admin las gestione.</li>
 * </ul>
 *
 * <p><b>Implementacion conservadora:</b> en lugar de hacer DELETE fisico (que
 * romperia la cadena de hashes), marca los logs como archivados (soft archive).
 * Esto preserva la cadena hash y permite auditoria posterior. Para purga fisica
 * real (cumplimiento Habeas Data) se requeriria mover a tabla audit_logs_archive
 * antes de borrar — fuera del alcance actual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final AuditLogRepository logRepository;
    private final AuditRetentionPolicyRepository policyRepository;
    private final AuditPurgeRecordRepository purgeRepository;

    /** Cache de politicas activas. */
    private final AtomicReference<List<AuditRetentionPolicy>> cachedPolicies = new AtomicReference<>();

    /** Tamano max del lote a purgar por ejecucion del scheduler. */
    private static final int PURGE_BATCH_SIZE = 1000;

    /**
     * HU-AU-10 E2: calcula retention_until segun la primera politica que matchea.
     * Orden de especificidad: modulo+severidad → severidad sola → modulo solo → wildcard.
     * Si no hay politica que aplique, retorna null (sin expiracion).
     */
    public LocalDateTime calculateRetentionUntil(AuditModule module, AuditSeverity severity) {
        List<AuditRetentionPolicy> policies = getPolicies();
        AuditRetentionPolicy best = null;
        int bestSpecificity = -1;
        for (AuditRetentionPolicy p : policies) {
            if (p.getMatchModule() != null && p.getMatchModule() != module) continue;
            if (p.getMatchSeverity() != null && p.getMatchSeverity() != severity) continue;
            int spec = (p.getMatchModule() != null ? 2 : 0) + (p.getMatchSeverity() != null ? 1 : 0);
            if (spec > bestSpecificity) {
                best = p;
                bestSpecificity = spec;
            }
        }
        if (best == null) return null;
        return LocalDateTime.now().plusDays(best.getRetentionDays());
    }

    private List<AuditRetentionPolicy> getPolicies() {
        List<AuditRetentionPolicy> cached = cachedPolicies.get();
        if (cached == null) {
            cached = policyRepository.findByEnabledTrue();
            cachedPolicies.set(cached);
        }
        return cached;
    }

    public void invalidatePolicyCache() { cachedPolicies.set(null); }

    // ─── Legal Hold (HU-AU-10 E7) ─────────────────────────

    @Transactional
    public ResponseEntity<?> setLegalHold(Long logId, String reason) {
        AuditLog log = logRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Log no encontrado"));
        if (log.getArchivedAt() != null) {
            throw new IllegalStateException(
                    "El registro ya fue purgado, no se puede aplicar legal hold");
        }
        log.setLegalHold(true);
        log.setLegalHoldReason(reason);
        logRepository.save(log);
        return ResponseEntity.ok(Map.of(
                "logId", logId, "legalHold", true, "reason", reason));
    }

    @Transactional
    public ResponseEntity<?> releaseLegalHold(Long logId) {
        AuditLog log = logRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Log no encontrado"));
        log.setLegalHold(false);
        log.setLegalHoldReason(null);
        logRepository.save(log);
        return ResponseEntity.ok(Map.of("logId", logId, "legalHold", false));
    }

    // ─── Purga programada (HU-AU-10 E2/E3/E4/E5/E6) ────────

    /**
     * HU-AU-10: Scheduler que corre cada dia a las 2:30 AM. Busca logs con
     * retencion expirada y los marca como archivados (preservando el hash chain).
     */
    @Scheduled(cron = "${sigcon.audit.purge-cron:0 30 2 * * *}")
    public void runPurgeScheduled() {
        log.info("HU-AU-10: iniciando purga programada de logs vencidos");
        try {
            executePurge("scheduler-cron");
        } catch (Exception e) {
            log.error("HU-AU-10 E4: error en purga programada", e);
        }
    }

    /**
     * Ejecucion manual de la purga (endpoint admin). Util para auditorias o
     * forzar limpieza fuera del horario programado.
     */
    @Transactional
    public ResponseEntity<?> executePurgeManual(String executedBy) {
        AuditPurgeRecord record = executePurge(executedBy);
        if (record == null) {
            return ResponseEntity.ok(Map.of(
                    "purged", 0,
                    "message", "Sin registros candidatos a purga en este momento"));
        }
        return ResponseEntity.ok(record);
    }

    @Transactional
    protected AuditPurgeRecord executePurge(String executedBy) {
        List<AuditLog> candidates = logRepository.findPurgeable(
                LocalDateTime.now(), PageRequest.of(0, PURGE_BATCH_SIZE));
        if (candidates.isEmpty()) return null;

        // HU-AU-10 E5/E6: hash SHA-256 del lote (concatenacion de hashes)
        StringBuilder concatHashes = new StringBuilder();
        LocalDateTime oldest = candidates.get(0).getTimestamp();
        LocalDateTime newest = oldest;
        for (AuditLog l : candidates) {
            concatHashes.append(l.getHash());
            if (l.getTimestamp().isBefore(oldest)) oldest = l.getTimestamp();
            if (l.getTimestamp().isAfter(newest)) newest = l.getTimestamp();
        }
        String batchHash = sha256(concatHashes.toString());

        // Marcar como archivados (preservando hash chain)
        LocalDateTime now = LocalDateTime.now();
        for (AuditLog l : candidates) {
            l.setArchivedAt(now);
            l.setArchivedHash(batchHash);
        }
        logRepository.saveAll(candidates);

        // Registrar evidencia
        AuditPurgeRecord record = AuditPurgeRecord.builder()
                .purgeDate(now)
                .recordsPurged(candidates.size())
                .oldestPurged(oldest)
                .newestPurged(newest)
                .batchHash(batchHash)
                .executedBy(executedBy)
                .notes("Purga automatica de " + candidates.size() + " logs vencidos. "
                        + "Marcados como archivados (soft archive). Hash chain preservada.")
                .build();
        record = purgeRepository.save(record);

        log.info("HU-AU-10 E5/E6: purga completada. Registros={}, batch_hash={}, "
                + "rango={} a {}, executed_by={}",
                candidates.size(), batchHash.substring(0, 8), oldest, newest, executedBy);
        return record;
    }

    public ResponseEntity<?> listPurgeRecords() {
        return ResponseEntity.ok(purgeRepository.findTop20ByOrderByPurgeDateDesc());
    }

    // ─── CRUD de politicas ───────────────────────────────

    public ResponseEntity<?> listPolicies() {
        return ResponseEntity.ok(policyRepository.findAll());
    }

    @Transactional
    public ResponseEntity<?> createPolicy(AuditRetentionPolicy policy, String createdBy) {
        if (policy.getRetentionDays() == null || policy.getRetentionDays() <= 0) {
            throw new IllegalArgumentException(
                    "Error en configuracion de politica de retencion. Operacion cancelada. "
                    + "retention_days debe ser positivo (HU-AU-10 E8).");
        }
        policy.setCreatedBy(createdBy);
        policy.setEnabled(policy.getEnabled() == null ? true : policy.getEnabled());
        AuditRetentionPolicy saved = policyRepository.save(policy);
        invalidatePolicyCache();
        log.info("HU-AU-10 E1: politica creada id={} retention={}d",
                saved.getId(), saved.getRetentionDays());
        return ResponseEntity.ok(saved);
    }

    @Transactional
    public ResponseEntity<?> updatePolicy(Long id, AuditRetentionPolicy update) {
        if (update.getRetentionDays() == null || update.getRetentionDays() <= 0) {
            throw new IllegalArgumentException(
                    "Error en configuracion de politica de retencion. Operacion cancelada.");
        }
        AuditRetentionPolicy existing = policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Politica no encontrada"));
        existing.setName(update.getName());
        existing.setDescription(update.getDescription());
        existing.setMatchModule(update.getMatchModule());
        existing.setMatchSeverity(update.getMatchSeverity());
        existing.setRetentionDays(update.getRetentionDays());
        existing.setLegalBasis(update.getLegalBasis());
        existing.setEnabled(update.getEnabled());
        AuditRetentionPolicy saved = policyRepository.save(existing);
        invalidatePolicyCache();
        return ResponseEntity.ok(saved);
    }

    @Transactional
    public ResponseEntity<?> deletePolicy(Long id) {
        AuditRetentionPolicy existing = policyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Politica no encontrada"));
        existing.setDeletedAt(LocalDateTime.now());
        policyRepository.save(existing);
        invalidatePolicyCache();
        return ResponseEntity.ok().build();
    }

    /** HU-AU-10 E10: estado actual del ciclo de vida (activos/archivados/legal hold). */
    public ResponseEntity<?> getStatus() {
        long total = logRepository.count();
        long withLegalHold = logRepository.countByLegalHoldTrue();
        Map<String, Object> status = new HashMap<>();
        status.put("totalLogs", total);
        status.put("legalHoldCount", withLegalHold);
        status.put("activePolicies", policyRepository.findByEnabledTrue().size());
        status.put("recentPurges", purgeRepository.findTop20ByOrderByPurgeDateDesc().size());
        return ResponseEntity.ok(status);
    }

    // ─── Hash util ─────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
}

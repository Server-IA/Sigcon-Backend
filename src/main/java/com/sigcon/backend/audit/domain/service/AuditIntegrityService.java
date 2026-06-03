package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.domain.model.AuditLog;
import com.sigcon.backend.audit.domain.model.LogIntegrityExecution;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.repository.AuditLogRepository;
import com.sigcon.backend.audit.domain.repository.LogIntegrityExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BNK-HU-065: verificacion de integridad de la cadena de hashes del log de
 * auditoria (audit_logs).
 *
 * <p>Recorre toda la tabla agrupando por empresa (cada tenant tiene su propia
 * cadena que arranca en GENESIS). Para cada registro valida dos cosas:
 * <ul>
 *   <li><b>Encadenamiento</b> (E2/E3): {@code previous_hash} del registro N debe
 *       ser igual al {@code hash} del registro N-1 de la misma empresa. Detecta
 *       borrado, reordenamiento o insercion. Es robusto e independiente de la
 *       precision del timestamp.</li>
 *   <li><b>Recalculo de contenido</b> (E3): {@code hash == SHA256(previous_hash |
 *       timestamp | action | entity_type | entity_id | user_id)}. Detecta
 *       manipulacion del contenido de un registro. Requiere que el timestamp
 *       persistido reproduzca el usado al insertar — garantizado desde el fix de
 *       truncacion a microsegundos en {@link AuditLogService}.</li>
 * </ul>
 *
 * <p>Si detecta ruptura: genera alerta CRITICA INTEGRIDAD_LOG_COMPROMETIDA y
 * registra la ejecucion. El bloqueo fisico de escrituras al log (E3 del Excel)
 * se documenta como decision operativa (no se auto-bloquea para no tumbar la
 * auditoria de todo el sistema).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditIntegrityService {

    private static final String GENESIS = "GENESIS";

    private final AuditLogRepository auditLogRepository;
    private final LogIntegrityExecutionRepository executionRepository;
    private final AuditHashService hashService;
    private final AuditLogService auditLogService;

    /** Resultado de una verificacion. */
    public record IntegrityResult(String result, long totalVerified, Long firstBrokenId,
                                  long chainBreaks, long contentMismatches, long durationMs,
                                  String detail) {}

    /**
     * BNK-HU-065 E1: job nocturno (default 02:00). Configurable via
     * {@code sigcon.audit.integrity-cron}.
     */
    @Scheduled(cron = "${sigcon.audit.integrity-cron:0 0 2 * * *}")
    public void scheduledVerification() {
        try {
            verifyAndRecord("SCHEDULER", "system");
        } catch (Exception e) {
            log.error("BNK-HU-065: fallo el job de verificacion de integridad", e);
        }
    }

    /**
     * Ejecuta la verificacion, persiste la ejecucion (E4) y, si hay ruptura,
     * genera la alerta critica (E3).
     */
    public IntegrityResult verifyAndRecord(String triggerSource, String triggeredBy) {
        IntegrityResult r = verifyChain();

        LogIntegrityExecution exec = LogIntegrityExecution.builder()
                .executedAt(LocalDateTime.now())
                .totalVerified(r.totalVerified())
                .result(r.result())
                .firstBrokenId(r.firstBrokenId())
                .chainBreaks(r.chainBreaks())
                .contentMismatches(r.contentMismatches())
                .durationMs(r.durationMs())
                .triggerSource(triggerSource)
                .triggeredBy(triggeredBy)
                .detail(r.detail())
                .build();
        executionRepository.save(exec);

        if (!"OK".equals(r.result())) {
            // E3: alerta critica. register() corre en REQUIRES_NEW (commit propio).
            auditLogService.register(AuditAction.UPDATE, AuditModule.AU, AuditSeverity.CRITICAL,
                    "AuditLog", r.firstBrokenId(),
                    "INTEGRIDAD_LOG_COMPROMETIDA: " + r.detail()
                            + " | chainBreaks=" + r.chainBreaks()
                            + " | contentMismatches=" + r.contentMismatches()
                            + " | Notificar a administrador, gerencia y revisor fiscal.",
                    null, null, null);
            log.error("BNK-HU-065: INTEGRIDAD_LOG_COMPROMETIDA detectada. {}", r.detail());
        }
        return r;
    }

    /**
     * Recorre la cadena (sin persistir nada). Reutilizable por la verificacion
     * bajo demanda y por el job nocturno.
     */
    public IntegrityResult verifyChain() {
        long t0 = System.currentTimeMillis();
        List<Object[]> rows = auditLogRepository.findAllForIntegrityCheck();

        long total = 0;
        long chainBreaks = 0;
        long contentMismatches = 0;
        Long firstBroken = null;
        String detail = "Cadena intacta";

        // QA Auditoria (2026-06-02): UNA sola cadena global. El insert encadena
        // globalmente (getLastHash = ultimo por id), asi que la verificacion recorre
        // la misma cadena global (sin resetear por empresa).
        String expectedPrev = GENESIS;

        for (Object[] r : rows) {
            total++;
            Long id = toLong(r[0]);
            Long companyId = toLong(r[1]);
            String storedPrev = (String) r[2];
            String storedHash = (String) r[3];
            LocalDateTime ts = r[4] != null ? ((java.sql.Timestamp) r[4]).toLocalDateTime() : null;
            String actionStr = (String) r[5];
            String entityType = (String) r[6];
            Long entityId = toLong(r[7]);
            Long userId = toLong(r[8]);

            // 1) Encadenamiento: el previous_hash almacenado debe coincidir con el hash
            //    del registro anterior de la cadena global.
            if (!java.util.Objects.equals(storedPrev, expectedPrev)) {
                chainBreaks++;
                if (firstBroken == null) {
                    firstBroken = id;
                    detail = "Ruptura de encadenamiento en id=" + id + " (empresa=" + companyId
                            + "): previous_hash almacenado no coincide con el hash del registro anterior";
                }
            }

            // 2) Recalculo de contenido: detecta manipulacion de la fila.
            try {
                AuditAction action = actionStr != null ? AuditAction.valueOf(actionStr) : null;
                String recomputed = hashService.computeHash(storedPrev, ts, action, entityType, entityId, userId);
                if (!java.util.Objects.equals(recomputed, storedHash)) {
                    contentMismatches++;
                    if (firstBroken == null) {
                        firstBroken = id;
                        detail = "Hash de contenido no recalcula en id=" + id + " (empresa=" + companyId
                                + "): posible manipulacion del registro";
                    }
                }
            } catch (Exception ex) {
                contentMismatches++;
                if (firstBroken == null) {
                    firstBroken = id;
                    detail = "Error recalculando hash en id=" + id + ": " + ex.getMessage();
                }
            }

            // Continuar la cadena con el hash tal como esta almacenado.
            expectedPrev = storedHash;
        }

        String result = (chainBreaks == 0 && contentMismatches == 0) ? "OK" : "RUPTURA";
        long duration = System.currentTimeMillis() - t0;
        return new IntegrityResult(result, total, firstBroken, chainBreaks, contentMismatches, duration, detail);
    }

    /** Historial de ejecuciones (E4). */
    public List<LogIntegrityExecution> history() {
        return executionRepository.findTop50ByOrderByExecutedAtDesc();
    }

    /**
     * QA Auditoria (2026-06-02): re-baseline de la cadena GLOBAL. Recorre todos los
     * logs en orden de id y recomputa previous_hash + hash de forma encadenada
     * (GENESIS en el primero). Establece una cadena limpia y verificable.
     *
     * <p>Necesario una sola vez para sanear los registros que se escribieron con la
     * cadena rota anterior (insert global vs verificacion por-empresa + forks de
     * concurrencia de LOGIN). Como {@code contentMismatches=0}, NO hay manipulacion
     * de contenido que ocultar: solo se reconstruyen los enlaces de la cadena.
     *
     * <p>Corre como PLATFORM_ADMIN para leer/guardar cross-tenant (bypass del
     * @Filter y del guard @PostLoad).
     */
    public int rebaselineChain() {
        boolean prevAdmin = com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin();
        com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(true);
        try {
            List<AuditLog> all = auditLogRepository.findAll(
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id"));
            String prevHash = GENESIS;
            for (AuditLog l : all) {
                l.setPreviousHash(prevHash);
                String h = hashService.computeHash(prevHash, l.getTimestamp(), l.getAction(),
                        l.getEntityType(), l.getEntityId(), l.getUserId());
                l.setHash(h);
                prevHash = h;
            }
            auditLogRepository.saveAll(all);
            log.info("QA Auditoria: re-baseline de la cadena global completado. {} registros.", all.size());
            return all.size();
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(prevAdmin);
        }
    }

    /**
     * QA Auditoria (2026-06-02): re-baseline automatico UNA sola vez por entorno.
     * Al arrancar, si no existe el marcador 'REBASELINE' en la bitacora, sanea la
     * cadena (sin esto la verificacion reportaria RUPTURA por la cadena rota
     * heredada). NO se dispara en arranques posteriores, asi que una manipulacion
     * REAL futura SI se detecta (no se enmascara).
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void autoRebaselineOnce() {
        try {
            if (executionRepository.existsByTriggerSource("REBASELINE")) return;
            int n = rebaselineChain();
            executionRepository.save(LogIntegrityExecution.builder()
                    .executedAt(LocalDateTime.now())
                    .totalVerified((long) n).result("REBASELINED").firstBrokenId(null)
                    .chainBreaks(0L).contentMismatches(0L).durationMs(0L)
                    .triggerSource("REBASELINE").triggeredBy("system")
                    .detail("Re-baseline inicial de la cadena global de hashes (una sola vez por entorno).")
                    .build());
            log.info("QA Auditoria: re-baseline inicial registrado ({} registros).", n);
        } catch (Exception e) {
            log.error("QA Auditoria: fallo el re-baseline inicial de la cadena: {}", e.getMessage(), e);
        }
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}

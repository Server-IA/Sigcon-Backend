package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.application.AuditDashboardDTO;
import com.sigcon.backend.audit.application.AuditLogDTO;
import com.sigcon.backend.audit.application.AuditLogFilterRequest;
import com.sigcon.backend.audit.domain.model.AuditLog;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.repository.AuditLogRepository;
import com.sigcon.backend.utils.UserUtil;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * HU-AU-01/02/05/07/09: Servicio central del modulo de auditoria.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Registrar eventos con hash SHA-256 encadenado (HU-AU-01)</li>
 *   <li>Capturar metadatos tecnicos IP/User-Agent (HU-AU-02)</li>
 *   <li>Busqueda avanzada con filtros (HU-AU-05)</li>
 *   <li>Datos del dashboard (HU-AU-07)</li>
 *   <li>Consulta por entidad o asiento contable (HU-AU-09)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final AuditHashService hashService;
    private final UserUtil userUtil;
    private final RiskRuleService riskRuleService;
    private final RetentionService retentionService;

    /**
     * QA Auditoria (2026-06-02): lock JVM para SERIALIZAR la insercion de logs y
     * evitar "forks" en la cadena de hashes. Sin esto, dos eventos casi simultaneos
     * (ej. dos LOGIN, o un VIEW de busqueda) leen el mismo getLastHash() y producen
     * dos registros con el mismo previous_hash -> RUPTURA en la verificacion.
     *
     * <p>Se envuelve la llamada al PROXY transaccional ({@code self.registerTx})
     * para que el lock se libere DESPUES del commit (el siguiente hilo lee la
     * cadena ya extendida). Un advisory lock dentro del @Transactional no bastaba
     * porque {@code search()} llama a register() por self-invocation y bypasea el
     * proxy REQUIRES_NEW. Lock justo (fair) para preservar el orden. Single-node.
     */
    private final java.util.concurrent.locks.ReentrantLock chainLock =
            new java.util.concurrent.locks.ReentrantLock(true);

    /** Auto-referencia al proxy para que registerTx() respete @Transactional. */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private AuditLogService self;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository journalEntryRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.assets.niif_alerts.domain.repository.NiifVerificationRepository niifVerificationRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.assets.niif_alerts.domain.repository.NiifAlertRepository niifAlertRepository;

    /**
     * HU-AU-01: Registra un evento de auditoria inmutable con hash encadenado.
     *
     * @param action         tipo de accion (CREATE, UPDATE, DELETE, etc.)
     * @param module         modulo origen (PA, TER, AP, AR, etc.)
     * @param severity       nivel de criticidad (null = auto-clasificar)
     * @param entityType     nombre de la entidad afectada
     * @param entityId       id de la entidad
     * @param description    texto legible del evento
     * @param oldValues      JSON con estado anterior (null para CREATE)
     * @param newValues      JSON con estado nuevo (null para DELETE)
     * @param journalEntryId id del asiento contable vinculado (null si no aplica)
     * @return el log registrado
     */
    public AuditLog register(AuditAction action, AuditModule module,
                              AuditSeverity severity, String entityType,
                              Long entityId, String description,
                              String oldValues, String newValues,
                              Long journalEntryId) {
        // QA Auditoria (2026-06-02): serializa la cadena con un lock JVM que envuelve
        // la llamada al PROXY transaccional. El lock se libera DESPUES del commit de
        // registerTx (el siguiente hilo lee la cadena ya extendida -> sin forks).
        // self = proxy, asi registerTx() respeta @Transactional aunque register()
        // se invoque por self-invocation (ej. desde search()).
        chainLock.lock();
        try {
            return self.registerTx(action, module, severity, entityType, entityId,
                    description, oldValues, newValues, journalEntryId);
        } finally {
            chainLock.unlock();
        }
    }

    /** Cuerpo transaccional de register(). NO llamar directo: usar register() (toma el lock). */
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AuditLog registerTx(AuditAction action, AuditModule module,
                              AuditSeverity severity, String entityType,
                              Long entityId, String description,
                              String oldValues, String newValues,
                              Long journalEntryId) {
        // Resolver usuario actual
        Long userId = null;
        String userEmail = null;
        try {
            var user = userUtil.getUser();
            userId = user.getId();
            userEmail = user.getEmail();
        } catch (Exception e) {
            userEmail = "system";
        }

        // HU-AU-04: si no se especifica severidad, primero buscar regla configurable
        // que matchee. Si no hay regla, usar la clasificacion estatica por defecto.
        if (severity == null) {
            AuditSeverity ruleSeverity = riskRuleService.classify(module, action, entityType);
            severity = ruleSeverity != null ? ruleSeverity : resolveDefaultSeverity(action);
        }

        // BNK-HU-065 (2026-05-20): truncar a microsegundos ANTES de calcular el hash.
        // PostgreSQL almacena timestamp con precision de microsegundos; si se hashea
        // con nanosegundos el hash deja de ser reproducible al releer (la verificacion
        // de integridad daria falsa ruptura). Truncar aqui hace la cadena verificable
        // por recalculo. El encadenamiento (previous_hash) ya era verificable.
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        // HU-AU-09 E2 (2026-04-28): si llega journalEntryId, validar que exista.
        // Antes era una FK logica sin verificacion: cualquier id (o uno borrado)
        // se persistia. Ahora se descarta el ID si el JE no esta presente.
        if (journalEntryId != null && journalEntryRepository != null) {
            try {
                if (!journalEntryRepository.existsById(journalEntryId)) {
                    log.warn("AuditLog: journalEntryId={} no existe en BD, se descarta del log",
                             journalEntryId);
                    journalEntryId = null;
                }
            } catch (Exception ignored) {
                // Si la verificacion falla por algun problema transversal, persistir
                // tal cual (fallback defensivo, no rompe el audit principal).
            }
        }

        // Hash encadenado SHA-256 (HU-AU-01 E5/E6)
        String previousHash = hashService.getLastHash();
        String hash = hashService.computeHash(
                previousHash, now, action, entityType, entityId, userId);

        // HU-AU-10 E2: calcular retention_until segun politica aplicable
        LocalDateTime retentionUntil = retentionService.calculateRetentionUntil(module, severity);

        AuditLog entry = AuditLog.builder()
                .timestamp(now)
                .userId(userId)
                .userEmail(userEmail)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .module(module)
                .severity(severity)
                .description(truncate(description, 500))
                .oldValues(oldValues)
                .newValues(newValues)
                .ipAddress(resolveIpAddress())
                .userAgent(truncate(resolveUserAgent(), 500))
                .hash(hash)
                .previousHash(previousHash)
                .journalEntryId(journalEntryId)
                .retentionUntil(retentionUntil)
                .build();

        entry = repository.save(entry);
        log.debug("Audit: [{}] {} {} {} id={} severity={} hash={}",
                module, action, entityType, entityId, entry.getId(), severity, hash.substring(0, 8));

        // HU-AU-09 E5 (2026-04-28): FK bidireccional. Si el log esta vinculado a
        // un JE, popular tambien JE.audit_log_id apuntando al log recien creado.
        if (journalEntryId != null && journalEntryRepository != null) {
            try {
                final Long auditLogId = entry.getId();
                final Long jeId = journalEntryId;
                journalEntryRepository.findById(jeId).ifPresent(je -> {
                    if (je.getAuditLogId() == null) {
                        je.setAuditLogId(auditLogId);
                        journalEntryRepository.save(je);
                    }
                });
            } catch (Exception e) {
                log.warn("HU-AU-09 E5: no se pudo poblar JE.auditLogId: {}", e.getMessage());
            }
        }
        return entry;
    }

    /** Detalle de un evento. */
    public AuditLogDTO findById(Long id) {
        return repository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new IllegalArgumentException("Evento de auditoria no encontrado"));
    }

    /** Ultimos N eventos. */
    public List<AuditLogDTO> findLatest(int limit) {
        return repository.findTop10ByOrderByTimestampDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /** Historial de una entidad (HU-AU-09). */
    public List<AuditLogDTO> findByEntity(String entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** Eventos vinculados a un asiento contable (HU-AU-09). */
    public List<AuditLogDTO> findByJournalEntry(Long journalEntryId) {
        return repository.findByJournalEntryIdOrderByTimestampDesc(journalEntryId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * HU-AU-05: Busqueda avanzada con filtros.
     *
     * <p>HU-AU-05 E5 (2026-04-28): la consulta se autorregistra como evento
     * {@code VIEW} con descripcion que resume los filtros aplicados, para que
     * un auditor externo pueda saber quien busco que datos.
     */
    public Page<AuditLogDTO> search(AuditLogFilterRequest filter, Pageable pageable) {
        Specification<AuditLog> spec = buildSpecification(filter);
        Page<AuditLogDTO> result = repository.findAll(spec, pageable).map(this::toDTO);
        // Registrar la consulta como evento VIEW para forensia.
        try {
            String summary = describeFilter(filter, result.getTotalElements());
            register(AuditAction.VIEW, AuditModule.AU, AuditSeverity.LOW,
                     "AuditLog", null,
                     "Consulta avanzada de logs: " + summary,
                     null, null, null);
        } catch (Exception e) {
            log.warn("No se pudo registrar VIEW de consulta de logs: {}", e.getMessage());
        }
        return result;
    }

    private String describeFilter(AuditLogFilterRequest f, long total) {
        List<String> parts = new ArrayList<>();
        if (f.getUserId() != null)        parts.add("userId=" + f.getUserId());
        if (f.getUserEmail() != null && !f.getUserEmail().isBlank()) parts.add("user~" + f.getUserEmail());
        if (f.getAction() != null)        parts.add("action=" + f.getAction());
        if (f.getModule() != null)        parts.add("module=" + f.getModule());
        if (f.getModules() != null && !f.getModules().isEmpty()) parts.add("modules=" + f.getModules());
        if (f.getSeverity() != null)      parts.add("sev=" + f.getSeverity());
        if (f.getSeverities() != null && !f.getSeverities().isEmpty()) parts.add("severities=" + f.getSeverities());
        if (f.getEntityType() != null && !f.getEntityType().isBlank()) parts.add("entity=" + f.getEntityType());
        if (f.getDateFrom() != null)      parts.add("from=" + f.getDateFrom());
        if (f.getDateTo() != null)        parts.add("to=" + f.getDateTo());
        if (f.getIpAddress() != null && !f.getIpAddress().isBlank()) parts.add("ip~" + f.getIpAddress());
        if (f.getUserAgent() != null && !f.getUserAgent().isBlank()) parts.add("ua~" + f.getUserAgent());
        if (f.getSearchText() != null && !f.getSearchText().isBlank()) parts.add("text~" + f.getSearchText());
        return (parts.isEmpty() ? "(sin filtros)" : String.join(", ", parts)) + " -> " + total + " resultados";
    }

    /** HU-AU-07: Datos del dashboard. */
    public AuditDashboardDTO getDashboardData() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        repository.countBySeveritySince(since).forEach(r ->
                bySeverity.put(r[0].toString(), (Long) r[1]));

        Map<String, Long> byModule = new LinkedHashMap<>();
        repository.countByModuleSince(since).forEach(r ->
                byModule.put(r[0].toString(), (Long) r[1]));

        Map<String, Long> byAction = new LinkedHashMap<>();
        repository.countByActionSince(since).forEach(r ->
                byAction.put(r[0].toString(), (Long) r[1]));

        // HU-AU-07 E4 (2026-04-28): metricas NIIF/SOX
        Long niifVerificationsTotal = 0L;
        Long niifAlertsOpen = 0L;
        Double niifCompliancePct = 0.0;
        try {
            if (niifVerificationRepository != null) {
                niifVerificationsTotal = niifVerificationRepository.count();
                if (niifVerificationsTotal > 0) {
                    long compliant = niifVerificationRepository.findAll().stream()
                            .filter(v -> v.getResult() != null
                                    && v.getResult().name().equals("COMPLIANT"))
                            .count();
                    niifCompliancePct = (compliant * 100.0) / niifVerificationsTotal;
                }
            }
            if (niifAlertRepository != null) {
                niifAlertsOpen = niifAlertRepository.count();
            }
        } catch (Exception e) {
            log.warn("HU-AU-07 E4: error calculando metricas NIIF: {}", e.getMessage());
        }

        // SOX-like: % eventos NO criticos sobre el total ult. 30 dias
        Long criticalEventsRecent = bySeverity.getOrDefault("CRITICAL", 0L);
        Long totalRecent = bySeverity.values().stream().mapToLong(Long::longValue).sum();
        Double soxControlPct = totalRecent == 0 ? 100.0
                : ((totalRecent - criticalEventsRecent) * 100.0) / totalRecent;

        return AuditDashboardDTO.builder()
                .totalEvents(repository.count())
                .countBySeverity(bySeverity)
                .countByModule(byModule)
                .countByAction(byAction)
                .latestEvents(findLatest(10))
                .niifVerificationsTotal(niifVerificationsTotal)
                .niifAlertsOpen(niifAlertsOpen)
                .niifCompliancePct(niifCompliancePct)
                .soxControlPct(soxControlPct)
                .criticalEventsRecent(criticalEventsRecent)
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────

    private AuditSeverity resolveDefaultSeverity(AuditAction action) {
        return switch (action) {
            case LOGIN, LOGOUT, VIEW, EXPORT -> AuditSeverity.LOW;
            case CREATE, UPDATE -> AuditSeverity.MEDIUM;
            case DELETE -> AuditSeverity.HIGH;
        };
    }

    // HU-AU-02 E2 (2026-04-28): validar formato IP antes de almacenar.
    // IPv4: 1.2.3.4 | IPv6: 2001:db8::1 | localhost: ::1, 127.0.0.1
    private static final Pattern IPV4_RX =
            Pattern.compile("^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$");
    private static final Pattern IPV6_RX =
            Pattern.compile("^[0-9a-fA-F:]{2,}$");

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String t = ip.trim();
        if ("::1".equals(t) || "localhost".equalsIgnoreCase(t)) return true;
        if (IPV4_RX.matcher(t).matches()) return true;
        return IPV6_RX.matcher(t).matches() && t.contains(":");
    }

    private String resolveIpAddress() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                String candidate = forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
                // HU-AU-02 E2: si la IP capturada es invalida (ej. inyeccion via
                // X-Forwarded-For), almacenar marca de invalidez en lugar del valor
                // malformado para preservar integridad y trazabilidad.
                if (!isValidIp(candidate)) {
                    log.warn("AuditLog: IP invalida descartada: '{}'", candidate);
                    return "INVALID_IP";
                }
                return candidate;
            }
        } catch (Exception ignored) {}
        return "N/A (event-driven)";
    }

    private String resolveUserAgent() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest().getHeader("User-Agent");
            }
        } catch (Exception ignored) {}
        return "N/A (event-driven)";
    }

    private Specification<AuditLog> buildSpecification(AuditLogFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.getUserId() != null) predicates.add(cb.equal(root.get("userId"), f.getUserId()));
            if (f.getUserEmail() != null && !f.getUserEmail().isBlank())
                predicates.add(cb.like(cb.lower(root.get("userEmail")),
                        "%" + f.getUserEmail().toLowerCase() + "%"));
            if (f.getAction() != null) predicates.add(cb.equal(root.get("action"), f.getAction()));
            if (f.getModule() != null) predicates.add(cb.equal(root.get("module"), f.getModule()));
            if (f.getSeverity() != null) predicates.add(cb.equal(root.get("severity"), f.getSeverity()));
            if (f.getEntityType() != null && !f.getEntityType().isBlank())
                predicates.add(cb.equal(root.get("entityType"), f.getEntityType()));
            if (f.getEntityId() != null) predicates.add(cb.equal(root.get("entityId"), f.getEntityId()));
            if (f.getDateFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), f.getDateFrom()));
            if (f.getDateTo() != null) predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), f.getDateTo()));
            if (f.getSearchText() != null && !f.getSearchText().isBlank())
                predicates.add(cb.like(cb.lower(root.get("description")),
                        "%" + f.getSearchText().toLowerCase() + "%"));
            // HU-AU-02 E4: filtros tecnicos por IP / User-Agent
            if (f.getIpAddress() != null && !f.getIpAddress().isBlank())
                predicates.add(cb.like(cb.lower(root.get("ipAddress")),
                        "%" + f.getIpAddress().toLowerCase() + "%"));
            if (f.getUserAgent() != null && !f.getUserAgent().isBlank())
                predicates.add(cb.like(cb.lower(root.get("userAgent")),
                        "%" + f.getUserAgent().toLowerCase() + "%"));
            // HU-AU-05 E3: filtros multi-valor combinados (modulo + severidad)
            if (f.getModules() != null && !f.getModules().isEmpty())
                predicates.add(root.get("module").in(f.getModules()));
            if (f.getSeverities() != null && !f.getSeverities().isEmpty())
                predicates.add(root.get("severity").in(f.getSeverities()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AuditLogDTO toDTO(AuditLog e) {
        return AuditLogDTO.builder()
                .id(e.getId())
                .timestamp(e.getTimestamp())
                .userId(e.getUserId())
                .userEmail(e.getUserEmail())
                .action(e.getAction() != null ? e.getAction().name() : null)
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .module(e.getModule() != null ? e.getModule().name() : null)
                .severity(e.getSeverity() != null ? e.getSeverity().name() : null)
                .description(e.getDescription())
                .oldValues(e.getOldValues())
                .newValues(e.getNewValues())
                .ipAddress(e.getIpAddress())
                .userAgent(e.getUserAgent())
                .hash(e.getHash())
                .previousHash(e.getPreviousHash())
                .journalEntryId(e.getJournalEntryId())
                .legalHold(e.getLegalHold())
                .legalHoldReason(e.getLegalHoldReason())
                .retentionUntil(e.getRetentionUntil())
                .build();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}

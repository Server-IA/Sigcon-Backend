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
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AuditLog register(AuditAction action, AuditModule module,
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

        LocalDateTime now = LocalDateTime.now();

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

    /** HU-AU-05: Busqueda avanzada con filtros. */
    public Page<AuditLogDTO> search(AuditLogFilterRequest filter, Pageable pageable) {
        Specification<AuditLog> spec = buildSpecification(filter);
        return repository.findAll(spec, pageable).map(this::toDTO);
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

        return AuditDashboardDTO.builder()
                .totalEvents(repository.count())
                .countBySeverity(bySeverity)
                .countByModule(byModule)
                .countByAction(byAction)
                .latestEvents(findLatest(10))
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

    private String resolveIpAddress() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
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
                .build();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}

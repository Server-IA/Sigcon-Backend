package com.sigcon.backend.parametrization.temporary_permissions.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission;
import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission.Status;
import com.sigcon.backend.parametrization.temporary_permissions.domain.repository.TemporaryPermissionRepository;
import com.sigcon.backend.parametrization.users.domain.model.Permission;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HU-PA-13/14/15/16/17 — gestion de permisos temporales.
 *
 * <p>Reglas de negocio implementadas:
 * <ul>
 *   <li>HU-PA-13 E2: max 90 dias de duracion.</li>
 *   <li>HU-PA-13 E3: max 3 ACTIVE simultaneos por usuario.</li>
 *   <li>HU-PA-13 E4: justificacion >= 30 chars.</li>
 *   <li>HU-PA-13 E5: startDate futuro permitido.</li>
 *   <li>HU-PA-13 E6: ADITIVO al rol base (ver {@code computeEffective}).</li>
 *   <li>HU-PA-13 E7: receptor NO puede asignarlos (validado en controller via @PreAuthorize).</li>
 *   <li>HU-PA-14 E2: revocacion requiere justificacion >= 30 chars.</li>
 *   <li>HU-PA-14 E3: bloqueo si ya esta EXPIRED/REVOKED.</li>
 *   <li>HU-PA-15: scheduler en {@link TemporaryPermissionExpiryScheduler}.</li>
 *   <li>HU-PA-16: listado paginado en {@link #search}.</li>
 *   <li>HU-PA-17 E4: usuario ve solo los suyos en {@link #getMyActive}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemporaryPermissionService {

    private static final int MAX_ACTIVE_PER_USER = 3;
    private static final int MAX_DURATION_DAYS = 90;
    private static final int MIN_JUSTIFICATION_CHARS = 30;

    private final TemporaryPermissionRepository repository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final AuditPublisher auditPublisher;
    /** HU-PA-20: notificacion personal al receptor del permiso. Inyectado por setter para evitar ciclo. */
    private com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotificationService(
            com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * HU-PA-13: crea uno o mas permisos temporales. Cada permiso atomico genera
     * una fila independiente para auditoria y revocacion granular.
     *
     * @return lista de IDs creados (uno por permiso atomico)
     */
    @Transactional
    public List<Long> grant(Long userId, List<Long> permissionIds, String justification,
                            LocalDateTime startDate, LocalDateTime endDate) {
        // E4: justification min 30 chars
        if (justification == null || justification.trim().length() < MIN_JUSTIFICATION_CHARS) {
            throw new IllegalArgumentException(
                "La justificación es obligatoria y debe tener al menos "
                + MIN_JUSTIFICATION_CHARS + " caracteres");
        }
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un permiso");
        }
        // Defaults
        LocalDateTime now = LocalDateTime.now();
        if (startDate == null) startDate = now;
        if (endDate == null) {
            throw new IllegalArgumentException("La fecha de fin es obligatoria");
        }
        // E2: end > start, max 90 dias
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior a la de inicio");
        }
        long days = Duration.between(startDate, endDate).toDays();
        if (days > MAX_DURATION_DAYS) {
            throw new IllegalArgumentException(
                "La duración máxima de un permiso temporal es de "
                + MAX_DURATION_DAYS + " días. Ajuste la fecha de fin");
        }

        // Validar usuario receptor
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException("El usuario receptor no esta activo");
        }
        // Aislamiento tenant: el receptor debe ser de la misma empresa que el grantor
        Long currentTenant = TenantContext.getCompanyId();
        if (currentTenant != null && user.getCompanyId() != null
                && !currentTenant.equals(user.getCompanyId())) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }

        // E3: max 3 ACTIVE
        long activeCount = repository.countByUserIdAndStatusAndDeletedAtIsNull(userId, Status.ACTIVE);
        if (activeCount + permissionIds.size() > MAX_ACTIVE_PER_USER) {
            List<TemporaryPermission> existing = repository
                    .findByUserIdAndStatusOrderByEndDateAsc(userId, Status.ACTIVE);
            String existingList = existing.stream()
                    .map(t -> t.getPermissionCode() + " (vence " + t.getEndDate() + ")")
                    .collect(Collectors.joining(", "));
            throw new MaxActiveReachedException(
                "El usuario ya tiene el máximo de " + MAX_ACTIVE_PER_USER
                + " asignaciones temporales activas. Espere a que venza alguna o "
                + "revóquela manualmente para asignar una nueva. Vigentes: ["
                + existingList + "]");
        }

        // Resolver permisos validos (existen). Permission tiene @SQLDelete pero no expone deletedAt.
        List<Permission> perms = permissionRepository.findAllById(permissionIds);
        if (perms.size() != permissionIds.size()) {
            throw new IllegalArgumentException("Uno o mas permisos no son validos");
        }

        // Resolver actor
        Long grantorId = null;
        String grantorEmail = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User u) {
            grantorId = u.getId();
            grantorEmail = u.getEmail();
        }

        List<Long> createdIds = new ArrayList<>();
        Long companyId = user.getCompanyId();
        for (Permission p : perms) {
            TemporaryPermission tp = TemporaryPermission.builder()
                    .companyId(companyId)
                    .userId(userId)
                    .permissionId(p.getId())
                    .permissionCode(p.getCode())
                    .grantedByUserId(grantorId)
                    .grantedByEmail(grantorEmail)
                    .justification(justification.trim())
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(Status.ACTIVE)
                    .build();
            tp = repository.save(tp);
            createdIds.add(tp.getId());

            auditPublisher.publishCreate(AuditModule.PA, "TemporaryPermission", tp.getId(),
                "TEMP_PERMISSION_GRANTED userId=" + userId
              + " perm=" + p.getCode() + " end=" + endDate
              + " by=" + grantorEmail + " reason=" + justification.trim());

            // HU-PA-20: notificacion personal al receptor.
            if (notificationService != null) {
                try {
                    notificationService.publishToUser(userId,
                        com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                            .companyId(companyId)
                            .eventKey("TEMP_PERMISSION_ASSIGNED")
                            .title("Le fue asignado un permiso temporal")
                            .body("Permiso: " + p.getCode() + " | Vence: " + endDate
                                + " | Justificacion: " + justification.trim())
                            .actionUrl("/perfil#permisos-temporales")
                            .sourceId(tp.getId())
                            .sourceType("TemporaryPermission")
                            .build());
                } catch (RuntimeException ex) {
                    log.warn("[NOTIF] No se pudo publicar TEMP_PERMISSION_ASSIGNED: {}", ex.getMessage());
                }
            }
        }
        log.info("HU-PA-13: {} permisos temporales otorgados a userId={} por {}",
                createdIds.size(), userId, grantorEmail);
        return createdIds;
    }

    /**
     * HU-PA-14: revoca un permiso temporal. Justificacion obligatoria >= 30 chars.
     */
    @Transactional
    public void revoke(Long temporaryPermissionId, String reason) {
        if (reason == null || reason.trim().length() < MIN_JUSTIFICATION_CHARS) {
            throw new IllegalArgumentException(
                "La justificación de revocación es obligatoria (mínimo "
                + MIN_JUSTIFICATION_CHARS + " caracteres)");
        }
        TemporaryPermission tp = repository.findById(temporaryPermissionId)
                .orElseThrow(() -> new IllegalArgumentException("Permiso temporal no encontrado"));
        if (tp.getDeletedAt() != null) {
            throw new IllegalArgumentException("Permiso temporal eliminado");
        }
        // Aislamiento tenant
        Long currentTenant = TenantContext.getCompanyId();
        if (currentTenant != null && tp.getCompanyId() != null
                && !currentTenant.equals(tp.getCompanyId())) {
            throw new IllegalArgumentException("Permiso temporal no encontrado");
        }
        // E3: solo ACTIVE es revocable
        if (tp.getStatus() != Status.ACTIVE) {
            throw new IllegalStateException(
                "El permiso ya no está activo. Estado actual: " + tp.getStatus());
        }

        Long actorId = null;
        String actorEmail = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User u) {
            actorId = u.getId();
            actorEmail = u.getEmail();
        }

        tp.setStatus(Status.REVOKED);
        tp.setRevokedAt(LocalDateTime.now());
        tp.setRevokedByUserId(actorId);
        tp.setRevokedByEmail(actorEmail);
        tp.setRevocationReason(reason.trim());
        repository.save(tp);

        auditPublisher.publishUpdate(AuditModule.PA, "TemporaryPermission", tp.getId(),
            "TEMP_PERMISSION_REVOKED userId=" + tp.getUserId()
          + " perm=" + tp.getPermissionCode()
          + " by=" + actorEmail + " reason=" + reason.trim());

        // HU-PA-20: notificacion personal al receptor avisando la revocacion.
        if (notificationService != null) {
            try {
                notificationService.publishToUser(tp.getUserId(),
                    com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                        .companyId(tp.getCompanyId())
                        .eventKey("TEMP_PERMISSION_REVOKED")
                        .title("Su permiso temporal fue revocado")
                        .body("Permiso: " + tp.getPermissionCode() + " | Motivo: " + reason.trim())
                        .actionUrl("/perfil#permisos-temporales")
                        .sourceId(tp.getId())
                        .sourceType("TemporaryPermission")
                        .severity(com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.WARNING)
                        .build());
            } catch (RuntimeException ex) {
                log.warn("[NOTIF] No se pudo publicar TEMP_PERMISSION_REVOKED: {}", ex.getMessage());
            }
        }
        log.info("HU-PA-14: permiso temporal {} revocado por {}", tp.getId(), actorEmail);
    }

    /**
     * HU-PA-13 E6 + HU-PA-17: codes de permisos efectivos del usuario AHORA
     * (ACTIVE + dentro de la ventana). ADITIVO — el caller debe unirlos al
     * conjunto de permisos del rol.
     */
    @Transactional(readOnly = true)
    public Set<String> computeEffectiveCodes(Long userId) {
        return repository.findActiveAtMoment(userId, LocalDateTime.now()).stream()
                .map(TemporaryPermission::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * HU-PA-17: lista de los permisos temporales activos del usuario actual
     * (para mostrar en su perfil). NO devuelve los de otros usuarios.
     */
    @Transactional(readOnly = true)
    public List<TemporaryPermission> getMyActive(Long currentUserId) {
        return repository.findByUserIdAndStatusOrderByEndDateAsc(currentUserId, Status.ACTIVE);
    }

    /**
     * HU-PA-16: listado historial paginado con filtros opcionales.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TemporaryPermission> search(
            Long userIdFilter, Status statusFilter,
            LocalDateTime from, LocalDateTime to,
            org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<TemporaryPermission> spec =
                (root, q, cb) -> {
                    var preds = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    preds.add(cb.isNull(root.get("deletedAt")));
                    if (userIdFilter != null) preds.add(cb.equal(root.get("userId"), userIdFilter));
                    if (statusFilter != null) preds.add(cb.equal(root.get("status"), statusFilter));
                    if (from != null) preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
                    if (to != null) preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
                    return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
                };
        return repository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public TemporaryPermission findById(Long id) {
        TemporaryPermission tp = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Permiso temporal no encontrado"));
        // QA Bloque PA Bug 44 (HU-PA-17 E4, 2026-05-09): aislar cross-tenant. Si
        // el actor no es PLATFORM_ADMIN y el permiso pertenece a otra empresa, NO
        // exponer la fila (404 generico, no 403, para no revelar existencia).
        if (!TenantContext.isPlatformAdmin()) {
            Long currentTenant = TenantContext.getCompanyId();
            if (currentTenant != null && tp.getCompanyId() != null
                    && !currentTenant.equals(tp.getCompanyId())) {
                throw new IllegalArgumentException("Permiso temporal no encontrado");
            }
        }
        return tp;
    }

    /**
     * QA Bloque PA Bug 45 (HU-PA-16 E6, 2026-05-09): construye el timeline
     * cronologico de eventos del permiso temporal. Incluye creacion, expiracion
     * por job nocturno (si aplica) y revocacion manual (si aplica). Sirve como
     * evidencia unica para auditoria externa.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTimeline(Long temporaryPermissionId) {
        TemporaryPermission tp = findById(temporaryPermissionId);
        List<Map<String, Object>> events = new ArrayList<>();

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("event", "GRANTED");
        created.put("at", tp.getCreatedAt());
        created.put("byUserId", tp.getGrantedByUserId());
        created.put("byEmail", tp.getGrantedByEmail());
        created.put("description", "Permiso temporal otorgado: " + tp.getPermissionCode());
        created.put("justification", tp.getJustification());
        events.add(created);

        if (tp.getStatus() == Status.REVOKED) {
            Map<String, Object> rev = new LinkedHashMap<>();
            rev.put("event", "REVOKED");
            rev.put("at", tp.getRevokedAt());
            rev.put("byUserId", tp.getRevokedByUserId());
            rev.put("byEmail", tp.getRevokedByEmail());
            rev.put("description", "Permiso temporal revocado manualmente antes de su vencimiento");
            rev.put("reason", tp.getRevocationReason());
            events.add(rev);
        } else if (tp.getStatus() == Status.EXPIRED) {
            Map<String, Object> exp = new LinkedHashMap<>();
            exp.put("event", "EXPIRED");
            exp.put("at", tp.getEndDate());
            exp.put("byUserId", null);
            exp.put("byEmail", "system");
            exp.put("description", "Permiso temporal vencido automaticamente (job nocturno)");
            events.add(exp);
        }

        return events;
    }

    /** Excepcion especifica para mapear a HTTP 409 (HU-PA-13 E3). */
    public static class MaxActiveReachedException extends RuntimeException {
        public MaxActiveReachedException(String message) { super(message); }
    }
}

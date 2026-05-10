package com.sigcon.backend.parametrization.notifications.domain.service;

import com.sigcon.backend.parametrization.notifications.application.NotificationDTO;
import com.sigcon.backend.parametrization.notifications.application.PublishEventRequest;
import com.sigcon.backend.parametrization.notifications.domain.model.Notification;
import com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity;
import com.sigcon.backend.parametrization.notifications.domain.model.Notification.Type;
import com.sigcon.backend.parametrization.notifications.domain.model.NotificationEventCatalog;
import com.sigcon.backend.parametrization.notifications.domain.repository.NotificationEventCatalogRepository;
import com.sigcon.backend.parametrization.notifications.domain.repository.NotificationRepository;
import com.sigcon.backend.parametrization.notifications.domain.repository.RoleNotificationSubscriptionRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * HU-PA-19/20/21/22/25 — nucleo del sistema de notificaciones in-app.
 *
 * <p>Tres operaciones principales:
 * <ul>
 *   <li>{@link #publishToUser(Long, PublishEventRequest)}: HU-PA-20 (USER_EVENT, no configurable).</li>
 *   <li>{@link #publishByRoleSubscription(PublishEventRequest)}: HU-PA-19 (ROL_EVENT, expandido a todos los users con rol suscrito).</li>
 *   <li>{@link #listForUser(Long, String, Boolean, int, int)}: HU-PA-21 bandeja paginada.</li>
 * </ul>
 *
 * <p>Deduplicacion (HU-PA-25): antes de insertar, se verifica si existe
 * notificacion con (user_id, event_key, source_id) en los ultimos
 * {@code dedupWindowSeconds}. Si existe, se omite la insercion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEventCatalogRepository catalogRepository;
    private final RoleNotificationSubscriptionRepository subscriptionRepository;
    /** HU-PA-21 push opcional via SSE. Inyeccion opcional. */
    private NotificationPushHub pushHub;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPushHub(NotificationPushHub pushHub) {
        this.pushHub = pushHub;
    }

    /** HU-PA-25: ventana de deduplicacion en segundos. Configurable via property. */
    @Value("${sigcon.parametrization.notifications.dedup-window-seconds:60}")
    private int dedupWindowSeconds;

    /** HU-PA-24: vida util en dias. Default 30. */
    @Value("${sigcon.parametrization.notifications.ttl-days:30}")
    private int ttlDays;

    // =============================================================
    // PUBLISH
    // =============================================================

    /**
     * HU-PA-20: publica una notificacion personal a un usuario especifico.
     * No depende de suscripcion de rol; siempre se envia.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationDTO> publishToUser(Long userId, PublishEventRequest req) {
        if (userId == null) throw new IllegalArgumentException("userId obligatorio");
        validateRequest(req);
        NotificationEventCatalog evt = lookupEvent(req.getEventKey());
        if (isDuplicate(userId, req.getEventKey(), req.getSourceId())) {
            log.debug("[NOTIF] Dedup HU-PA-25: skip evt={} user={} source={}",
                    req.getEventKey(), userId, req.getSourceId());
            return Optional.empty();
        }
        Notification n = persist(userId, req, evt, Type.USER_EVENT);
        return Optional.of(NotificationDTO.from(n));
    }

    /**
     * HU-PA-19 E5 (Bloque PA Bug 47, 2026-05-09): version asincrona del publish
     * por suscripcion de rol. Se invoca con {@code @Async} para no bloquear la
     * transaccion del modulo origen (CG/AR/AP/BNK/NOM). El TenantContext puede
     * NO estar propagado en el thread async, por eso se requiere companyId
     * explicito en el request.
     *
     * <p>Cualquier service que dispare un evento de negocio debe llamar este
     * metodo en lugar de {@link #publishByRoleSubscription} si la operacion es
     * critica y el bloqueo por persistencia de notificaciones afectaria UX.
     */
    @org.springframework.scheduling.annotation.Async
    public void publishByRoleSubscriptionAsync(PublishEventRequest req) {
        try {
            // Propagar tenant explicitamente para que el @Filter de Hibernate aplique.
            com.sigcon.backend.platform.tenant.TenantContext.setCompanyId(req.getCompanyId());
            publishByRoleSubscription(req);
        } catch (Exception ex) {
            log.error("[NOTIF] Async publishByRoleSubscription fallo: evt={} company={}",
                    req.getEventKey(), req.getCompanyId(), ex);
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.clear();
        }
    }

    /**
     * HU-PA-19: publica notificacion a todos los usuarios de la empresa cuyo
     * rol este suscrito al evento. Aplica dedup HU-PA-25 por usuario.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationDTO> publishByRoleSubscription(PublishEventRequest req) {
        validateRequest(req);
        NotificationEventCatalog evt = lookupEvent(req.getEventKey());
        Long companyId = req.getCompanyId();
        if (companyId == null) {
            throw new IllegalArgumentException("companyId obligatorio para ROL_EVENT");
        }
        List<Long> userIds = subscriptionRepository.findUserIdsSubscribedToEvent(req.getEventKey(), companyId);
        if (userIds.isEmpty()) {
            log.debug("[NOTIF] Sin suscriptores para evento {} company={}", req.getEventKey(), companyId);
            return Collections.emptyList();
        }
        List<NotificationDTO> created = new ArrayList<>();
        for (Long uid : userIds) {
            if (isDuplicate(uid, req.getEventKey(), req.getSourceId())) continue;
            Notification n = persist(uid, req, evt, Type.ROL_EVENT);
            created.add(NotificationDTO.from(n));
        }
        return created;
    }

    /** Permite a callers que ya saben quien recibe + tipo del evento, publicar manualmente. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationDTO> publishSystem(Long userId, PublishEventRequest req) {
        if (userId == null) throw new IllegalArgumentException("userId obligatorio");
        validateRequest(req);
        NotificationEventCatalog evt = lookupEvent(req.getEventKey());
        if (isDuplicate(userId, req.getEventKey(), req.getSourceId())) return Optional.empty();
        Notification n = persist(userId, req, evt, Type.SYSTEM);
        return Optional.of(NotificationDTO.from(n));
    }

    // =============================================================
    // LISTAR / MARCAR
    // =============================================================

    /**
     * HU-PA-21: bandeja paginada del usuario actual.
     *
     * <p>QA Bloque PA Bug 53 (HU-PA-21 E2, 2026-05-09): los filtros se combinan
     * via Specification en lugar de ser mutuamente excluyentes. Acepta module,
     * type (USER_EVENT/ROL_EVENT) y unreadOnly simultaneamente.
     */
    @Transactional(readOnly = true)
    public Page<NotificationDTO> listForUser(Long userId, String moduleFilter, Boolean unreadOnly,
                                             int page, int size) {
        return listForUser(userId, moduleFilter, null, unreadOnly, page, size);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDTO> listForUser(Long userId, String moduleFilter, String typeFilter,
                                             Boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime now = LocalDateTime.now();

        // Build Specification combinando filtros
        org.springframework.data.jpa.domain.Specification<Notification> spec = (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.equal(root.get("userId"), userId));
            preds.add(cb.isNull(root.get("deletedAt")));
            preds.add(cb.or(cb.isNull(root.get("expiresAt")), cb.greaterThan(root.get("expiresAt"), now)));
            if (Boolean.TRUE.equals(unreadOnly)) {
                preds.add(cb.isNull(root.get("readAt")));
            }
            if (moduleFilter != null && !moduleFilter.isBlank()) {
                preds.add(cb.equal(root.get("module"), moduleFilter));
            }
            if (typeFilter != null && !typeFilter.isBlank()) {
                try {
                    Notification.Type t = Notification.Type.valueOf(typeFilter.toUpperCase());
                    preds.add(cb.equal(root.get("type"), t));
                } catch (IllegalArgumentException ignored) {
                    // tipo invalido: no aplicar filtro (no rompe la query)
                }
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Notification> result = notificationRepository.findAll(spec, pageable);
        return result.map(NotificationDTO::from);
    }

    /** HU-PA-22: contador de no leidas para el badge. */
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countActiveUnread(userId, LocalDateTime.now());
    }

    /** HU-PA-22: marcar una notificacion como leida (solo el dueno). */
    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {
        Optional<Notification> opt = notificationRepository.findByIdAndUserId(notificationId, userId);
        if (opt.isEmpty()) return false;
        Notification n = opt.get();
        if (n.getReadAt() != null) return true; // ya leida, idempotente
        n.setReadAt(LocalDateTime.now());
        notificationRepository.save(n);
        return true;
    }

    /** HU-PA-22: marcar todas como leidas en una sola operacion. */
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllReadByUser(userId, LocalDateTime.now());
    }

    /** HU-PA-23: navegacion con marcado automatico como leida. */
    @Transactional
    public Optional<NotificationDTO> getAndMarkRead(Long notificationId, Long userId) {
        Optional<Notification> opt = notificationRepository.findByIdAndUserId(notificationId, userId);
        if (opt.isEmpty()) return Optional.empty();
        Notification n = opt.get();
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
        return Optional.of(NotificationDTO.from(n));
    }

    // =============================================================
    // INTERNAL
    // =============================================================

    private boolean isDuplicate(Long userId, String eventKey, Long sourceId) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(dedupWindowSeconds);
        return !notificationRepository.findRecentDuplicates(userId, eventKey, sourceId, since).isEmpty();
    }

    private NotificationEventCatalog lookupEvent(String eventKey) {
        return catalogRepository.findByEventKey(eventKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Evento desconocido: " + eventKey + " (verifica notification_event_catalog)"));
    }

    private Notification persist(Long userId, PublishEventRequest req, NotificationEventCatalog evt, Type type) {
        Long companyId = req.getCompanyId();
        if (companyId == null) {
            // Si quien llama no setea companyId pero hay TenantContext, usarlo (USER_EVENT).
            companyId = TenantContext.getCompanyId();
        }
        if (companyId == null) {
            throw new IllegalStateException("No se pudo resolver companyId para la notificacion");
        }
        Notification n = Notification.builder()
                .companyId(companyId)
                .userId(userId)
                .type(type)
                .module(evt.getModule())
                .eventKey(req.getEventKey())
                .title(req.getTitle())
                .body(req.getBody())
                .actionUrl(req.getActionUrl())
                .sourceId(req.getSourceId())
                .sourceType(req.getSourceType())
                .severity(req.getSeverity() != null ? req.getSeverity() : Severity.INFO)
                .expiresAt(LocalDateTime.now().plusDays(ttlDays))
                .build();
        Notification saved = notificationRepository.save(n);
        // HU-PA-21 push opcional via SSE (si el cliente esta conectado al stream).
        if (pushHub != null) {
            try { pushHub.push(userId, NotificationDTO.from(saved)); }
            catch (RuntimeException ex) { log.warn("[SSE] push fallo userId={}: {}", userId, ex.getMessage()); }
        }
        return saved;
    }

    private void validateRequest(PublishEventRequest req) {
        if (req == null) throw new IllegalArgumentException("PublishEventRequest nulo");
        if (req.getEventKey() == null || req.getEventKey().isBlank())
            throw new IllegalArgumentException("eventKey obligatorio");
        if (req.getTitle() == null || req.getTitle().isBlank())
            throw new IllegalArgumentException("title obligatorio");
        if (req.getTitle().length() > 160)
            throw new IllegalArgumentException("title supera 160 chars");
    }
}

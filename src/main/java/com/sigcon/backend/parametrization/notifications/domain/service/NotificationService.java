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

    /** HU-PA-21: bandeja paginada del usuario actual. */
    @Transactional(readOnly = true)
    public Page<NotificationDTO> listForUser(Long userId, String moduleFilter, Boolean unreadOnly,
                                             int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime now = LocalDateTime.now();
        Page<Notification> result;
        if (Boolean.TRUE.equals(unreadOnly)) {
            result = notificationRepository.findActiveUnreadByUser(userId, now, pageable);
        } else if (moduleFilter != null && !moduleFilter.isBlank()) {
            result = notificationRepository.findActiveByUserAndModule(userId, now, moduleFilter, pageable);
        } else {
            result = notificationRepository.findActiveByUser(userId, now, pageable);
        }
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

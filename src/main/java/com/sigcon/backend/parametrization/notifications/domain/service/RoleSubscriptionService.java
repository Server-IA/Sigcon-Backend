package com.sigcon.backend.parametrization.notifications.domain.service;

import com.sigcon.backend.parametrization.notifications.application.RoleSubscriptionDTO;
import com.sigcon.backend.parametrization.notifications.application.UpsertRoleSubscriptionRequest;
import com.sigcon.backend.parametrization.notifications.domain.model.NotificationEventCatalog;
import com.sigcon.backend.parametrization.notifications.domain.model.RoleNotificationSubscription;
import com.sigcon.backend.parametrization.notifications.domain.repository.NotificationEventCatalogRepository;
import com.sigcon.backend.parametrization.notifications.domain.repository.RoleNotificationSubscriptionRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * HU-PA-18: gestion de suscripciones de rol a eventos.
 *
 * <p>Bulk upsert: el ADMIN_EMPRESA define en el form de rol que eventos quiere
 * recibir, con que umbral (si aplica). Aqui se persisten las suscripciones.
 */
@Service
@RequiredArgsConstructor
public class RoleSubscriptionService {

    private final RoleNotificationSubscriptionRepository subscriptionRepository;
    private final NotificationEventCatalogRepository catalogRepository;

    /** Lista las suscripciones activas + inactivas de un rol. */
    @Transactional(readOnly = true)
    public List<RoleSubscriptionDTO> listForRole(Long roleId) {
        return subscriptionRepository.findByRoleId(roleId).stream()
                .map(RoleSubscriptionDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Crea o actualiza una suscripcion. Si ya existe (roleId, eventKey),
     * actualiza enabled + thresholdDays. Valida que el evento exista en catalogo
     * y que threshold solo se use si supports_threshold=true.
     */
    @Transactional
    public RoleSubscriptionDTO upsert(Long roleId, UpsertRoleSubscriptionRequest req) {
        if (roleId == null) throw new IllegalArgumentException("roleId obligatorio");
        if (req == null || req.getEventKey() == null || req.getEventKey().isBlank())
            throw new IllegalArgumentException("eventKey obligatorio");
        NotificationEventCatalog evt = catalogRepository.findByEventKey(req.getEventKey())
                .orElseThrow(() -> new IllegalArgumentException("Evento desconocido: " + req.getEventKey()));
        if (req.getThresholdDays() != null && Boolean.FALSE.equals(evt.getSupportsThreshold())) {
            throw new IllegalArgumentException(
                    "El evento " + req.getEventKey() + " no soporta umbral en dias");
        }
        if (req.getThresholdDays() != null && req.getThresholdDays() < 0) {
            throw new IllegalArgumentException("thresholdDays debe ser >= 0");
        }
        Optional<RoleNotificationSubscription> existing =
                subscriptionRepository.findByRoleIdAndEventKey(roleId, req.getEventKey());
        RoleNotificationSubscription s = existing.orElseGet(() -> RoleNotificationSubscription.builder()
                .roleId(roleId)
                .eventKey(req.getEventKey())
                .companyId(TenantContext.getCompanyId())
                .enabled(true)
                .build());
        s.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        s.setThresholdDays(req.getThresholdDays());
        return RoleSubscriptionDTO.from(subscriptionRepository.save(s));
    }

    @Transactional
    public boolean delete(Long roleId, String eventKey) {
        Optional<RoleNotificationSubscription> opt =
                subscriptionRepository.findByRoleIdAndEventKey(roleId, eventKey);
        if (opt.isEmpty()) return false;
        subscriptionRepository.delete(opt.get());
        return true;
    }
}

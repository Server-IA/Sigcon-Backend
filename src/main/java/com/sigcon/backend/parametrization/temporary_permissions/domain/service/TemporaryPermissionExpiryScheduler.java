package com.sigcon.backend.parametrization.temporary_permissions.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission;
import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission.Status;
import com.sigcon.backend.parametrization.temporary_permissions.domain.repository.TemporaryPermissionRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HU-PA-15 — job nocturno que expira permisos temporales vencidos
 * y notifica vencimientos proximos (24h).
 *
 * <p>Ejecucion:
 * <ul>
 *   <li>Cada 5 minutos verifica vencimientos y notificaciones (configurable).</li>
 *   <li>Idempotente: solo procesa registros aun ACTIVE con end_date < NOW (E2).</li>
 *   <li>Sin extension automatica (E3).</li>
 * </ul>
 *
 * <p>Tracking de la ultima ejecucion accesible para HU-PA-15 E5 (monitoreo).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TemporaryPermissionExpiryScheduler {

    private final TemporaryPermissionRepository repository;
    private final AuditPublisher auditPublisher;
    /** HU-PA-20: notificaciones personales (vence en 24h, vencido). Inyeccion opcional para no romper tests. */
    private com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotificationService(
            com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Snapshot de la ultima ejecucion (para HU-PA-15 E5 monitoreo). */
    private final AtomicReference<RunSummary> lastRun = new AtomicReference<>();

    /**
     * HU-PA-15 E1: cron cada 5 minutos. Configurable via
     * {@code sigcon.parametrization.temp-permission.expiry-cron}.
     */
    @Scheduled(cron = "${sigcon.parametrization.temp-permission.expiry-cron:0 */5 * * * *}")
    public void runScheduled() {
        runNow();
    }

    /**
     * Ejecucion manual (puede invocarse desde un endpoint admin si fallo el cron).
     * Devuelve resumen para HU-PA-15 E5.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunSummary runNow() {
        long t0 = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusHours(24);

        RunSummary summary = new RunSummary();
        summary.startedAt = now;

        try {
            // ----- Vencimientos -----
            List<TemporaryPermission> expired = repository.findExpired(now);
            for (TemporaryPermission tp : expired) {
                tp.setStatus(Status.EXPIRED);
                repository.save(tp);
                final TemporaryPermission ftp = tp;
                // Audit per-tenant
                if (ftp.getCompanyId() != null) {
                    TenantContext.runAs(ftp.getCompanyId(), false, () -> {
                        auditPublisher.publishUpdate(AuditModule.PA, "TemporaryPermission", ftp.getId(),
                            "TEMP_PERMISSION_EXPIRED userId=" + ftp.getUserId()
                          + " perm=" + ftp.getPermissionCode() + " endDate=" + ftp.getEndDate());
                        // HU-PA-20: notifica al receptor que su permiso vencio
                        if (notificationService != null) {
                            try {
                                // QA Bloque AV (Bug 4, 2026-05-14): NO actionUrl.
                                // Antes era "/perfil#permisos-temporales" que es
                                // una ruta inexistente y daba 404. Click marca
                                // como leida sin navegar.
                                notificationService.publishToUser(ftp.getUserId(),
                                    com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                                        .companyId(ftp.getCompanyId())
                                        .eventKey("TEMP_PERMISSION_EXPIRED")
                                        .title("Su permiso temporal vencio")
                                        .body("Permiso: " + ftp.getPermissionCode() + " | Vencio: " + ftp.getEndDate())
                                        .sourceId(ftp.getId())
                                        .sourceType("TemporaryPermission")
                                        .build());
                            } catch (RuntimeException ex) {
                                log.warn("[NOTIF] No se pudo publicar TEMP_PERMISSION_EXPIRED: {}", ex.getMessage());
                            }
                        }
                    });
                }
                summary.expiredCount++;
            }

            // ----- Notificaciones 24h antes (E4) -----
            List<TemporaryPermission> upcoming = repository.findUpcomingExpirations(now, soon);
            for (TemporaryPermission tp : upcoming) {
                tp.setExpiredNotified24h(true);
                repository.save(tp);
                final TemporaryPermission ftp = tp;
                if (ftp.getCompanyId() != null) {
                    TenantContext.runAs(ftp.getCompanyId(), false, () -> {
                        auditPublisher.publishUpdate(AuditModule.PA, "TemporaryPermission", ftp.getId(),
                            "TEMP_PERMISSION_EXPIRING_24H userId=" + ftp.getUserId()
                          + " perm=" + ftp.getPermissionCode() + " endDate=" + ftp.getEndDate());
                        // HU-PA-20: aviso 24h antes
                        if (notificationService != null) {
                            try {
                                // QA Bloque AV (Bug 4, 2026-05-14): NO actionUrl.
                                notificationService.publishToUser(ftp.getUserId(),
                                    com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                                        .companyId(ftp.getCompanyId())
                                        .eventKey("TEMP_PERMISSION_EXPIRING")
                                        .title("Su permiso temporal vence en 24h")
                                        .body("Permiso: " + ftp.getPermissionCode() + " | Vence: " + ftp.getEndDate())
                                        .sourceId(ftp.getId())
                                        .sourceType("TemporaryPermission")
                                        .severity(com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.WARNING)
                                        .build());
                            } catch (RuntimeException ex) {
                                log.warn("[NOTIF] No se pudo publicar TEMP_PERMISSION_EXPIRING: {}", ex.getMessage());
                            }
                        }
                    });
                }
                summary.notifiedCount++;
            }
            summary.status = "OK";
        } catch (RuntimeException ex) {
            summary.status = "FAILED";
            summary.errorMessage = ex.getMessage();
            log.error("HU-PA-15 scheduler failed: {}", ex.getMessage(), ex);
        }

        summary.durationMs = System.currentTimeMillis() - t0;
        summary.endedAt = LocalDateTime.now();
        lastRun.set(summary);
        if (summary.expiredCount > 0 || summary.notifiedCount > 0) {
            log.info("HU-PA-15 scheduler: expired={} notified={} duration={}ms status={}",
                    summary.expiredCount, summary.notifiedCount, summary.durationMs, summary.status);
        }
        return summary;
    }

    /** HU-PA-15 E5: ultimo resumen, accesible desde el endpoint de monitoreo. */
    public RunSummary getLastRun() { return lastRun.get(); }

    public static class RunSummary {
        public LocalDateTime startedAt;
        public LocalDateTime endedAt;
        public long durationMs;
        public int expiredCount;
        public int notifiedCount;
        public String status;
        public String errorMessage;
    }
}

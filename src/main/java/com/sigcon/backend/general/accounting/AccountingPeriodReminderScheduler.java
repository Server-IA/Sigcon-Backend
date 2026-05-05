package com.sigcon.backend.general.accounting;

import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HU-PA-19 + PERIOD_CLOSE_REMINDER: scheduler diario que detecta periodos
 * contables OPEN cuya fecha de cierre esta proxima (umbral configurable).
 *
 * <p>Ejecucion:
 * <ul>
 *   <li>Diario a las 08:30 AM (configurable via {@code sigcon.parametrization.period-reminder-cron}).</li>
 *   <li>Para cada periodo OPEN cuyo ultimo dia esta dentro del threshold, dispara
 *       {@code PERIOD_CLOSE_REMINDER} a roles suscritos del tenant.</li>
 *   <li>Threshold por defecto: 7 dias. La suscripcion del rol puede sobreescribirlo
 *       (campo {@code thresholdDays} de role_notification_subscriptions). Aqui solo
 *       calculamos un umbral conservador para evitar enviar a periodos lejanos.</li>
 *   <li>Dedup HU-PA-25 evita repetir notificaciones diarias del mismo periodo.</li>
 * </ul>
 *
 * <p>Por que 08:30 AM y no 03:00? Para que las notificaciones lleguen en horario laboral
 * y el contador las vea al iniciar el dia.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountingPeriodReminderScheduler {

    private final AccountingPeriodRepository periodRepository;
    /** HU-PA-19: notif PERIOD_CLOSE_REMINDER. Inyeccion opcional. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService;

    /** Umbral por defecto en dias. */
    private static final int DEFAULT_THRESHOLD_DAYS = 7;

    private final AtomicReference<RunSummary> lastRun = new AtomicReference<>();

    @Scheduled(cron = "${sigcon.parametrization.period-reminder-cron:0 30 8 * * *}")
    public void runScheduled() {
        runNow();
    }

    /** Llamada por el cron y por endpoint admin sin override; usa threshold default. */
    public RunSummary runNow() {
        return runNow(DEFAULT_THRESHOLD_DAYS);
    }

    /**
     * Variante que acepta un threshold custom (util para QA/smoke test). Usar
     * thresholdDays grande para validar la rama positiva sin esperar al cron.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunSummary runNow(int thresholdDays) {
        long t0 = System.currentTimeMillis();
        RunSummary summary = new RunSummary();
        summary.startedAt = java.time.LocalDateTime.now();

        // Multi-tenant (mismo patron que SalesInvoiceStatusScheduler): scheduler corre
        // sin TenantContext. Modo PLATFORM_ADMIN bypassa el @Filter de Hibernate.
        TenantContext.setPlatformAdmin(true);
        try {
            LocalDate today = LocalDate.now();
            // Periodos OPEN del mes actual o del anterior cuyo ultimo dia esta proximo
            List<AccountingPeriod> openPeriods = periodRepository.findByStatus(AccountingPeriodStatus.OPEN);
            for (AccountingPeriod p : openPeriods) {
                if (p.getYear() == null || p.getMonth() == null || p.getCompanyId() == null) continue;
                LocalDate lastDay = YearMonth.of(p.getYear(), p.getMonth()).atEndOfMonth();
                long daysUntilClose = java.time.temporal.ChronoUnit.DAYS.between(today, lastDay);
                if (daysUntilClose < 0 || daysUntilClose > thresholdDays) continue;
                summary.candidatesCount++;

                if (notificationService == null) continue;
                try {
                    final AccountingPeriod fp = p;
                    final long fdays = daysUntilClose;
                    TenantContext.runAs(fp.getCompanyId(), false, () ->
                        notificationService.publishByRoleSubscription(
                            com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                                .companyId(fp.getCompanyId())
                                .eventKey("PERIOD_CLOSE_REMINDER")
                                .title("Cierre de periodo proximo")
                                .body("Periodo " + fp.getYear() + "-" + String.format("%02d", fp.getMonth())
                                    + " cierra en " + fdays + " dia(s) (" + lastDay + ")")
                                .actionUrl("/contabilidad/periodos/" + fp.getId())
                                .sourceId(fp.getId())
                                .sourceType("AccountingPeriod")
                                .severity(fdays <= 1
                                    ? com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.CRITICAL
                                    : (fdays <= 3
                                        ? com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.WARNING
                                        : com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.INFO))
                                .build())
                    );
                    summary.notifiedCount++;
                } catch (RuntimeException ex) {
                    log.warn("[NOTIF] PERIOD_CLOSE_REMINDER no publicada para periodo id={}: {}",
                            p.getId(), ex.getMessage());
                }
            }
            summary.status = "OK";
        } catch (RuntimeException ex) {
            summary.status = "FAILED";
            summary.errorMessage = ex.getMessage();
            log.error("PERIOD_CLOSE_REMINDER scheduler failed: {}", ex.getMessage(), ex);
        } finally {
            TenantContext.clear();
        }
        summary.durationMs = System.currentTimeMillis() - t0;
        summary.endedAt = java.time.LocalDateTime.now();
        lastRun.set(summary);
        if (summary.candidatesCount > 0) {
            log.info("PERIOD_CLOSE_REMINDER: candidates={} notified={} duration={}ms",
                    summary.candidatesCount, summary.notifiedCount, summary.durationMs);
        }
        return summary;
    }

    public RunSummary getLastRun() { return lastRun.get(); }

    public static class RunSummary {
        public java.time.LocalDateTime startedAt;
        public java.time.LocalDateTime endedAt;
        public long durationMs;
        public int candidatesCount;
        public int notifiedCount;
        public String status;
        public String errorMessage;
    }
}

package com.sigcon.backend.parametrization.notifications.interfaces.controller;

import com.sigcon.backend.general.accounting.AccountingPeriodReminderScheduler;
import com.sigcon.backend.parametrization.notifications.domain.service.NotificationPurgeScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints admin para forzar la ejecucion manual de schedulers (HU-PA-21/24
 * + PERIOD_CLOSE_REMINDER). Solo PLATFORM_ADMIN o ADMIN_EMPRESA.
 */
@RestController
@RequestMapping("/api/parametrization/notifications/admin")
@RequiredArgsConstructor
@Tag(name = "Notificaciones admin", description = "Operaciones manuales de schedulers")
public class NotificationAdminController {

    private final AccountingPeriodReminderScheduler periodReminder;
    private final NotificationPurgeScheduler purgeScheduler;
    private final com.sigcon.backend.audit.domain.service.AuditPublisher auditPublisher;

    @PostMapping("/period-reminder/run")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Ejecuta PERIOD_CLOSE_REMINDER scheduler manualmente. Acepta threshold opcional para QA.")
    public ResponseEntity<?> runPeriodReminder(
            @org.springframework.web.bind.annotation.RequestParam(value = "thresholdDays", required = false)
            Integer thresholdDays) {
        AccountingPeriodReminderScheduler.RunSummary s = (thresholdDays != null && thresholdDays > 0)
                ? periodReminder.runNow(thresholdDays)
                : periodReminder.runNow();
        return ResponseEntity.ok(java.util.Map.of(
                "status", s.status,
                "candidatesCount", s.candidatesCount,
                "notifiedCount", s.notifiedCount,
                "durationMs", s.durationMs,
                "errorMessage", s.errorMessage == null ? "" : s.errorMessage
        ));
    }

    /**
     * Endpoint de prueba que dispara un audit_log severity=HIGH para validar el wire
     * AUDIT_RISK_ALERT end-to-end. Solo para QA/smoke test. NO usar en flujos de
     * negocio reales.
     */
    @PostMapping("/audit-risk-alert/test-fire")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "[QA] Dispara un audit_log HIGH para validar wire AUDIT_RISK_ALERT")
    public ResponseEntity<?> fireAuditRiskAlertTest() {
        long ts = System.currentTimeMillis();
        auditPublisher.publishDelete(
                com.sigcon.backend.audit.domain.model.enums.AuditModule.PA,
                "TestEntity",
                ts,
                "[QA SMOKE] Disparo manual de evento HIGH para validar AUDIT_RISK_ALERT wire @ " + ts);
        return ResponseEntity.ok(java.util.Map.of("fired", true, "ts", ts));
    }

    @PostMapping("/purge/run")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Ejecuta purga de notificaciones expiradas manualmente")
    public ResponseEntity<?> runPurge() {
        NotificationPurgeScheduler.RunSummary s = purgeScheduler.runNow();
        return ResponseEntity.ok(java.util.Map.of(
                "status", s.status,
                "deletedCount", s.deletedCount,
                "durationMs", s.durationMs
        ));
    }
}

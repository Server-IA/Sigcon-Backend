package com.sigcon.backend.parametrization.notifications.domain.service;

import com.sigcon.backend.parametrization.notifications.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HU-PA-24: job de mantenimiento que elimina notificaciones cuyo expires_at ya paso (>30 dias por default).
 *
 * <p>Por defecto corre todos los dias a las 03:30 AM. Configurable via
 * {@code sigcon.parametrization.notifications.purge-cron}.
 *
 * <p>Hard delete intencional: las notificaciones NO son evidencia legal
 * (cada accion ya queda registrada en audit_logs aparte). El propio
 * registro contiene un puntero {@code source_id} al recurso original.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPurgeScheduler {

    private final NotificationRepository notificationRepository;

    /**
     * QA Bloque PA Bug 55 (HU-PA-24 E3, 2026-05-09): hard cap de retencion en
     * dias. Por default 90d (lo que pide la HU). Configurable via
     * {@code sigcon.parametrization.notifications.retention-days}.
     */
    @org.springframework.beans.factory.annotation.Value("${sigcon.parametrization.notifications.retention-days:90}")
    private int retentionDays;

    private final AtomicReference<RunSummary> lastRun = new AtomicReference<>();

    @Scheduled(cron = "${sigcon.parametrization.notifications.purge-cron:0 30 3 * * *}")
    public void runScheduled() {
        runNow();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunSummary runNow() {
        long t0 = System.currentTimeMillis();
        RunSummary summary = new RunSummary();
        summary.startedAt = LocalDateTime.now();
        try {
            LocalDateTime now = LocalDateTime.now();
            // Fase 1: purga por expires_at vencida (TTL clasico)
            int deleted = notificationRepository.hardDeleteExpired(now);
            // QA Bloque PA Bug 54 (HU-PA-24 E2): backfill legacy expires_at IS NULL
            // (cualquier registro creado antes de que se introdujera la columna).
            // Cutoff = retentionDays dias (default 90). Mas conservador que la
            // purga clasica para no eliminar legacy aun fresca.
            int legacyDeleted = notificationRepository.hardDeleteLegacyOlderThan(now.minusDays(retentionDays));
            // QA Bloque PA Bug 55 (HU-PA-24 E3): hard cap absoluto por created_at
            // (HU pide max 90 dias retencion incluso si expires_at fue extendido).
            int hardCapDeleted = notificationRepository.hardDeleteOlderThanAbsolute(now.minusDays(retentionDays));
            summary.deletedCount = deleted + legacyDeleted + hardCapDeleted;
            summary.expiredDeleted = deleted;
            summary.legacyDeleted = legacyDeleted;
            summary.hardCapDeleted = hardCapDeleted;
            summary.retentionDaysApplied = retentionDays;
            summary.status = "OK";
            if (summary.deletedCount > 0) {
                log.info("HU-PA-24 notification purge: expired={} legacy={} hardCap={} retentionDays={} duration={}ms",
                        deleted, legacyDeleted, hardCapDeleted, retentionDays, System.currentTimeMillis() - t0);
            }
        } catch (RuntimeException ex) {
            summary.status = "FAILED";
            summary.errorMessage = ex.getMessage();
            log.error("HU-PA-24 notification purge failed: {}", ex.getMessage(), ex);
        }
        summary.durationMs = System.currentTimeMillis() - t0;
        summary.endedAt = LocalDateTime.now();
        lastRun.set(summary);
        return summary;
    }

    public RunSummary getLastRun() { return lastRun.get(); }

    public static class RunSummary {
        public LocalDateTime startedAt;
        public LocalDateTime endedAt;
        public long durationMs;
        public int deletedCount;
        public int expiredDeleted;
        public int legacyDeleted;
        public int hardCapDeleted;
        public int retentionDaysApplied;
        public String status;
        public String errorMessage;
    }
}

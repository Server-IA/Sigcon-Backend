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
            int deleted = notificationRepository.hardDeleteExpired(LocalDateTime.now());
            summary.deletedCount = deleted;
            summary.status = "OK";
            if (deleted > 0) {
                log.info("HU-PA-24 notification purge: deleted={} duration={}ms",
                        deleted, System.currentTimeMillis() - t0);
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
        public String status;
        public String errorMessage;
    }
}

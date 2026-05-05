package com.sigcon.backend.platform.audit.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * HU-PA-PLAT-08 E4: scheduler de retencion del log de plataforma.
 *
 * <p>Politica:
 * <ul>
 *   <li>Retiene minimo {@code sigcon.platform.audit.retention-years} anios
 *       (default 5, por Estatuto Tributario Art. 632).</li>
 *   <li>Despues del periodo, los registros se mueven a almacenamiento frio
 *       (en esta version: archivado logico via deleted_at; en produccion seria
 *       export a S3/Glacier antes de purga).</li>
 *   <li>Como {@code audit_log_platform} es inmutable (triggers BD bloquean
 *       UPDATE/DELETE), la "purga" en realidad es un export a CSV + un INSERT
 *       en {@code audit_log_platform_archived} de la cuenta. La fila original
 *       NO se borra.</li>
 * </ul>
 *
 * <p>Ejecucion: cron diario a las 03:00 AM (configurable). Logueado pero NO
 * destructivo en esta version — consulta cuantos registros estarian en el
 * proximo ciclo de archivado y reporta. La purga real se hace cuando se
 * configure el storage frio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformAuditRetentionScheduler {

    private final JdbcTemplate jdbcTemplate;

    @Value("${sigcon.platform.audit.retention-years:5}")
    private int retentionYears;

    /**
     * Cron 03:00 AM diario. Configurable via {@code sigcon.platform.audit.retention-cron}.
     */
    @Scheduled(cron = "${sigcon.platform.audit.retention-cron:0 0 3 * * *}")
    public void runRetentionAudit() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusYears(retentionYears);
            Integer eligible = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_log_platform WHERE occurred_at < ?",
                    Integer.class, cutoff);
            log.info("PlatformAuditRetention: cutoff={} eligibles={} (retencion={} anios). "
                   + "Esta version solo reporta, no destruye.",
                    cutoff, eligible, retentionYears);
            // NOTA: la HU dice que despues de N anios pueden archivarse a almacenamiento
            // frio pero "siguen consultables". En produccion deberia:
            //   1. Exportar a S3/Glacier los registros eligibles.
            //   2. Verificar checksum del export.
            //   3. (Opcional, requiere desactivar trigger) marcar como archived_at.
            // Por ahora solo reporta.
        } catch (RuntimeException ex) {
            log.warn("PlatformAuditRetention failed: {}", ex.getMessage());
        }
    }
}

package com.sigcon.backend.audit.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * HU-AU-09 E1 — Disparador automatico de la transferencia de logs de auditoria
 * a la BD externa de respaldo.
 *
 * <p><b>Deshabilitado por defecto</b> ({@code audit-backup.enabled=false}). El
 * scheduler solo se instancia cuando la propiedad es {@code true}, para que un
 * rebuild jamas comience a escribir en la BD externa sin habilitacion explicita.
 * El disparo manual (endpoint admin) funciona independientemente de este flag.
 *
 * <p>Cron configurable via {@code audit-backup.cron} (por defecto cada 5 minutos).
 */
@Component
@ConditionalOnProperty(name = "audit-backup.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class AuditBackupScheduler {

    private final AuditBackupTransferService service;

    @Scheduled(cron = "${audit-backup.cron:0 0/5 * * * *}")
    public void ejecutarTransferencia() {
        log.info("[AuditBackup][Scheduler] Iniciando transferencia de logs de auditoria...");
        try {
            service.transferir("SCHEDULER");
        } catch (Exception e) {
            // El servicio ya maneja sus reintentos; este catch evita que una falla
            // tumbe el hilo del scheduler.
            log.error("[AuditBackup][Scheduler] Error no controlado en la transferencia", e);
        }
    }
}

package com.sigcon.backend.integration.domain.service;

import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HU-INT-RF-13: Scheduler que reintenta el envio de ACKs fallidos al callback
 * de AgroFusion con backoff exponencial.
 *
 * <p>Cada 30 segundos consulta lotes en {@code ACK_PENDING} cuyo
 * {@code ack_next_retry_at} ya paso y dispara {@link AgroFusionAckClient#sendAck}.
 * El cliente actualiza {@code ack_retry_count} y calcula el siguiente
 * {@code ack_next_retry_at} con backoff (1*delay, 2*delay, 4*delay).
 *
 * <p>El scheduler corre en intervalos cortos (30s) pero solo procesa lotes
 * cuyo proximo retry ya vencio, por lo que el delay efectivo entre intentos
 * lo determina {@code AGROFUSION_ACK_RETRY_INITIAL_DELAY_SECONDS} (default 60s).
 *
 * <p>Cobertura HU-INT-RF-13:
 * <ul>
 *   <li>E1: Primer reintento tras fallo - el ACK falla → status ACK_PENDING,
 *       retry_count=1, next_retry=now+60s. El scheduler lo dispara cuando
 *       expira ese delay.</li>
 *   <li>E2: Backoff creciente - segundo intento falla → next_retry=now+120s.</li>
 *   <li>E3: Maximo de reintentos - tercer intento falla → status ACK_FAILED.</li>
 *   <li>E4: Exito en reintento - segundo o tercer intento responde 200 →
 *       status ACK_SENT y retry_count guardado en integration_batches.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AckRetryScheduler {

    private final IntegrationBatchRepository batchRepository;
    private final AgroFusionAckClient ackClient;

    /**
     * Ejecuta el ciclo de retry cada 30 segundos. La frecuencia del intervalo
     * es independiente del backoff: el scheduler corre frecuentemente para
     * minimizar la latencia entre el {@code ack_next_retry_at} y la accion
     * efectiva, pero solo procesa lotes cuyo proximo retry ya vencio.
     */
    @Scheduled(fixedDelayString = "${sigcon.integration.ack-retry-poll-millis:30000}")
    public void retryPendingAcks() {
        // Multi-tenant (Bloque G fix): el scheduler corre sin TenantContext.
        // Activamos modo PLATFORM_ADMIN para que @Filter NO restrinja la query
        // y veamos los pendientes de TODAS las empresas.
        com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(true);
        List<Long> pendingIds;
        try {
            pendingIds = batchRepository.findPendingAckRetryIds(LocalDateTime.now());
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.clear();
        }
        if (pendingIds.isEmpty()) return;

        log.info("AckRetryScheduler: encontrados {} lotes en ACK_PENDING listos para retry", pendingIds.size());
        for (Long id : pendingIds) {
            try {
                // Para cada batch, fijar el tenant del propio batch para que los
                // eventos derivados (audit logs, transfer history) queden con el
                // company_id correcto, no el default.
                Long companyId = batchRepository.findById(id).map(b -> b.getCompanyId()).orElse(null);
                com.sigcon.backend.platform.tenant.TenantContext.setCompanyId(companyId);
                com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(false);
                log.debug("Reintentando ACK para batch {} (empresa {})", id, companyId);
                // sendAck es @Transactional internamente y carga el payload dentro de la sesion
                ackClient.sendAck(id);
            } catch (Exception e) {
                log.error("Error en reintento de ACK para batch {}", id, e);
            } finally {
                com.sigcon.backend.platform.tenant.TenantContext.clear();
            }
        }
    }
}

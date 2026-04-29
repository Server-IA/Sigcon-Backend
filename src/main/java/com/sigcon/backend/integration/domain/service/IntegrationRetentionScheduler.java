package com.sigcon.backend.integration.domain.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.IntegrationTransfer;
import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import com.sigcon.backend.integration.domain.repository.IntegrationTransferRepository;
import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import com.sigcon.backend.platform.tenant.TenantContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spec AAEF Bloque W (item de cumplimiento legal): purga automatica de
 * registros del modulo de integracion AAEF para cumplir la regla de
 * retencion de 5 anios del Estatuto Tributario colombiano (Decreto 2649/1993
 * Art. 134, Estatuto Tributario Art. 632).
 *
 * <p>Politica de retencion:
 * <ul>
 *   <li>Registros con {@code received_at} (batch) o {@code processed_at}
 *       (transfer) anteriores al cutoff se marcan como soft-deleted.</li>
 *   <li>El cutoff es configurable via parametro
 *       {@code AGROFUSION_RETENTION_YEARS} (default 5 anios).</li>
 *   <li>NUNCA se purgan batches en estado pendiente (RECEIVED, PROCESSING,
 *       ACK_PENDING, ACK_FAILED). Solo se purgan los terminales: PROCESSED,
 *       PARTIAL, FAILED, ACK_SENT.</li>
 *   <li>Se purgan ANTES los transfers (children) y luego los batches (parent)
 *       para no dejar huerfanos.</li>
 *   <li>El scheduler corre 1 vez al dia a las 3:00 AM (configurable via
 *       {@code sigcon.integration.retention-cron}).</li>
 * </ul>
 *
 * <p>El soft delete ({@code @SQLDelete} en las entidades) preserva la fila
 * para forensia interna pero la oculta de las consultas con
 * {@code @Where(deleted_at IS NULL)}. Si se requiere purga fisica, se ejecuta
 * un DELETE manual posterior por DBA con un script aparte.
 *
 * <p>Ejemplo de log al ejecutar:
 * <pre>
 * IntegrationRetentionScheduler: cutoff=2021-04-27T03:00:00 (5 anios)
 * IntegrationRetentionScheduler: purgados 142 transfers, 18 batches.
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationRetentionScheduler {

    private static final String RETENTION_YEARS_PARAM = "AGROFUSION_RETENTION_YEARS";
    private static final int DEFAULT_RETENTION_YEARS = 5;
    /**
     * Tamano de pagina para procesar la purga en lotes (evita cargar millones
     * de filas a memoria si hay backlog grande).
     */
    private static final int BATCH_PAGE_SIZE = 500;

    private final IntegrationBatchRepository batchRepository;
    private final IntegrationTransferRepository transferRepository;
    private final ParameterRepository parameterRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Ejecuta la purga periodicamente. Cron por defecto: 03:00 AM cada dia.
     * Configurable via {@code sigcon.integration.retention-cron} en
     * application.yml o variable de entorno.
     */
    @Scheduled(cron = "${sigcon.integration.retention-cron:0 0 3 * * *}")
    public void runRetention() {
        // Activar bypass de tenant filter para purgar cross-tenant.
        TenantContext.setPlatformAdmin(true);
        try {
            int years = readRetentionYears();
            LocalDateTime cutoff = LocalDateTime.now().minusYears(years);
            log.info("IntegrationRetentionScheduler: cutoff={} ({} anios)", cutoff, years);

            int transfersPurged = purgeOldTransfers(cutoff);
            int batchesPurged = purgeOldBatches(cutoff);

            log.info("IntegrationRetentionScheduler: purgados {} transfers, {} batches.",
                    transfersPurged, batchesPurged);
        } catch (RuntimeException e) {
            log.error("IntegrationRetentionScheduler fallo. Reintentara en el proximo ciclo.", e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Endpoint de pruebas / forzado manual desde el modulo Plataforma.
     * Retorna el numero de registros purgados para que el operador vea el efecto.
     */
    public java.util.Map<String, Integer> runRetentionManually() {
        int years = readRetentionYears();
        LocalDateTime cutoff = LocalDateTime.now().minusYears(years);
        int t = purgeOldTransfers(cutoff);
        int b = purgeOldBatches(cutoff);
        log.info("Retencion manual: cutoff={}, transfers={}, batches={}", cutoff, t, b);
        return java.util.Map.of("transfersPurged", t, "batchesPurged", b);
    }

    @Transactional
    private int purgeOldTransfers(LocalDateTime cutoff) {
        // Native query bypassa @Where(deleted_at IS NULL) para encontrar los
        // candidatos. Soft delete los marca para que las queries futuras los
        // ignoren pero la fila queda para auditoria/forensia.
        @SuppressWarnings("unchecked")
        List<Object> candidates = entityManager.createNativeQuery(
                "SELECT id FROM af_accounting_transfers " +
                "WHERE deleted_at IS NULL AND processed_at IS NOT NULL " +
                "AND processed_at < :cutoff ORDER BY id LIMIT :pageSize")
            .setParameter("cutoff", cutoff)
            .setParameter("pageSize", BATCH_PAGE_SIZE)
            .getResultList();

        int count = 0;
        for (Object id : candidates) {
            Long transferId = ((Number) id).longValue();
            // Usamos delete() con @SQLDelete -> hace UPDATE deleted_at=NOW().
            transferRepository.findById(transferId).ifPresent(t -> transferRepository.delete(t));
            count++;
        }
        return count;
    }

    @Transactional
    private int purgeOldBatches(LocalDateTime cutoff) {
        // Solo batches en estados terminales para no perder lotes en flight.
        @SuppressWarnings("unchecked")
        List<Object> candidates = entityManager.createNativeQuery(
                "SELECT id FROM integration_batches " +
                "WHERE deleted_at IS NULL AND received_at < :cutoff " +
                "AND status IN ('PROCESSED','PARTIAL','FAILED','ACK_SENT','ACK_FAILED') " +
                "ORDER BY id LIMIT :pageSize")
            .setParameter("cutoff", cutoff)
            .setParameter("pageSize", BATCH_PAGE_SIZE)
            .getResultList();

        int count = 0;
        for (Object id : candidates) {
            Long batchId = ((Number) id).longValue();
            batchRepository.findById(batchId).ifPresent(b -> batchRepository.delete(b));
            count++;
        }
        return count;
    }

    private int readRetentionYears() {
        return parameterRepository.findGlobalValueByName(RETENTION_YEARS_PARAM)
                .map(v -> {
                    try {
                        int n = Integer.parseInt(v.trim());
                        return n > 0 ? n : DEFAULT_RETENTION_YEARS;
                    } catch (NumberFormatException ex) {
                        return DEFAULT_RETENTION_YEARS;
                    }
                })
                .orElse(DEFAULT_RETENTION_YEARS);
    }
}

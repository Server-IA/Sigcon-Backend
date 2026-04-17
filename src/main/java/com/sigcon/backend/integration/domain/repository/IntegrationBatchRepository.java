package com.sigcon.backend.integration.domain.repository;

import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para {@link IntegrationBatch}.
 *
 * <p>Expone consultas comunes usadas por el receptor AAEF y el procesador async:
 * busqueda por exchangeId (idempotencia), filtrado por estado (scheduler),
 * listado por sistema origen (dashboard admin).
 */
public interface IntegrationBatchRepository
        extends JpaRepository<IntegrationBatch, Long>,
                JpaSpecificationExecutor<IntegrationBatch> {

    /**
     * Busca un lote existente por su clave de idempotencia compuesta.
     * Usado en HU-INT-RF-03 para detectar reenvios.
     */
    Optional<IntegrationBatch> findByExchangeIdAndStandardVersionAndDeletedAtIsNull(
            String exchangeId, String standardVersion);

    /** Verifica si existe un lote con el exchangeId dado (HU-INT-RF-03). */
    boolean existsByExchangeIdAndStandardVersionAndDeletedAtIsNull(
            String exchangeId, String standardVersion);

    /** Lista lotes en estado dado para procesamiento async (usado por scheduler). */
    List<IntegrationBatch> findByStatusAndDeletedAtIsNull(BatchStatus status);

    /** Lista lotes pendientes de enviar ACK (status ACK_PENDING). */
    List<IntegrationBatch> findByStatusInAndDeletedAtIsNull(List<BatchStatus> statuses);

    /**
     * HU-INT-RF-13: ids de lotes en ACK_PENDING cuyo ack_next_retry_at ya paso
     * (o es null para el primer reintento manual). Usado por {@code AckRetryScheduler}.
     * Retorna solo IDs para evitar lazy-load del payload_json (LOB) fuera de transaccion.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT b.id FROM IntegrationBatch b WHERE b.deletedAt IS NULL " +
        "AND b.status = com.sigcon.backend.integration.domain.model.enums.BatchStatus.ACK_PENDING " +
        "AND (b.ackNextRetryAt IS NULL OR b.ackNextRetryAt <= :now)")
    List<Long> findPendingAckRetryIds(
        @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}

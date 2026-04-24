package com.sigcon.backend.integration.domain.repository;

import com.sigcon.backend.integration.domain.model.IntegrationTransfer;
import com.sigcon.backend.integration.domain.model.enums.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para {@link IntegrationTransfer}.
 */
public interface IntegrationTransferRepository
        extends JpaRepository<IntegrationTransfer, Long> {

    /** Lista todos los documentos de un lote. */
    List<IntegrationTransfer> findByBatch_IdAndDeletedAtIsNull(Long batchId);

    /** Busca un transfer por su documentId (externo AAEF). */
    Optional<IntegrationTransfer> findByDocumentIdAndDeletedAtIsNull(String documentId);

    /**
     * Pull+Diff (RF-INT-14): retorna el transfer MÁS RECIENTE de un document_id.
     *
     * <p>Spec: "Cuando llega un Pull+Diff sobre un document_id, SIGCON debe buscar
     * el asiento MÁS RECIENTE del documento (no el original del lote padre).
     * Pueden existir múltiples updates al mismo doc; siempre se reversa el último."
     *
     * <p>Solo considera transfers PROCESADOS (que tienen accountingEntryId no nulo).
     */
    Optional<IntegrationTransfer> findFirstByDocumentIdAndAccountingEntryIdIsNotNullAndDeletedAtIsNullOrderByProcessedAtDesc(
            String documentId);

    /** Lista transfers por estado (ej: FAILED para reintento manual - HU-INT-RF-15). */
    List<IntegrationTransfer> findByTransferStatusAndDeletedAtIsNull(TransferStatus status);

    /** Busca el transfer que produjo un accountingEntryId dado (HU-INT-RF-09). */
    Optional<IntegrationTransfer> findByAccountingEntryIdAndDeletedAtIsNull(Long accountingEntryId);
}

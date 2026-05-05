package com.sigcon.backend.invoices.attachments.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.invoices.attachments.domain.model.InvoiceAttachment;

/**
 * Repositorio JPA para {@link InvoiceAttachment}. AP-13.
 */
public interface InvoiceAttachmentRepository extends JpaRepository<InvoiceAttachment, Long> {

    /** Lista adjuntos vigentes de una factura. */
    List<InvoiceAttachment> findByInvoiceIdAndDeletedAtIsNull(Long invoiceId);

    /** Lista adjuntos vigentes de una factura filtrados por tipo de documento. */
    List<InvoiceAttachment> findByInvoiceIdAndDocumentTypeAndDeletedAtIsNull(
            Long invoiceId, String documentType);

    /**
     * HU-AP-12 E3 (Bloque AR): busca adjuntos con el mismo SHA-256 del
     * contenido para detectar duplicados por contenido. El @Filter de tenant
     * limita a la empresa actual (un mismo PDF puede convivir en empresas
     * distintas). Solo activos (deleted_at IS NULL) y no reemplazados
     * (replaced_by_id IS NULL).
     */
    List<InvoiceAttachment> findByFileHashAndReplacedByIdIsNullAndDeletedAtIsNull(String fileHash);
}

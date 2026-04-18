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
}

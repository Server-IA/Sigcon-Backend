package com.sigcon.backend.accounts_receivable.attachments.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.accounts_receivable.attachments.domain.model.SalesInvoiceAttachment;

/**
 * Repositorio JPA para {@link SalesInvoiceAttachment}.
 * AR-03: persistencia de comprobantes adjuntos a facturas de venta.
 */
public interface SalesInvoiceAttachmentRepository extends JpaRepository<SalesInvoiceAttachment, Long> {

    /**
     * Lista los adjuntos vigentes de una factura de venta.
     *
     * @param salesInvoiceId identificador de la factura
     * @return lista de adjuntos no eliminados
     */
    List<SalesInvoiceAttachment> findBySalesInvoiceIdAndDeletedAtIsNull(Long salesInvoiceId);
}

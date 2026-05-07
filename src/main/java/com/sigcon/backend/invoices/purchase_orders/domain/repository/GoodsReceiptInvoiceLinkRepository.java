package com.sigcon.backend.invoices.purchase_orders.domain.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReceiptInvoiceLink;

/**
 * Repositorio de vinculaciones parciales receipt<->invoice (HU-AP-19).
 */
@Repository
public interface GoodsReceiptInvoiceLinkRepository extends JpaRepository<GoodsReceiptInvoiceLink, Long> {

    List<GoodsReceiptInvoiceLink> findByReceiptIdAndDeletedAtIsNull(Long receiptId);

    List<GoodsReceiptInvoiceLink> findByInvoiceIdAndDeletedAtIsNull(Long invoiceId);

    Optional<GoodsReceiptInvoiceLink> findFirstByReceiptIdAndInvoiceIdAndDeletedAtIsNull(Long receiptId, Long invoiceId);

    /**
     * Suma del monto facturado contra una recepcion (links activos).
     * Devuelve null si la recepcion aun no tiene links.
     */
    @Query("SELECT COALESCE(SUM(l.invoicedAmount), 0) FROM GoodsReceiptInvoiceLink l "
         + "WHERE l.receiptId = :receiptId AND l.deletedAt IS NULL")
    BigDecimal sumInvoicedAmountByReceiptId(@Param("receiptId") Long receiptId);
}

package com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceLine;

/**
 * Repositorio de lineas de factura de venta.
 */
public interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, Long> {

    List<SalesInvoiceLine> findAllByInvoiceId(Long invoiceId);
}

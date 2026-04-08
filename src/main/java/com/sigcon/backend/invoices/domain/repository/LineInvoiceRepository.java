package com.sigcon.backend.invoices.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.invoices.domain.model.LinesInvoice;

public interface LineInvoiceRepository extends JpaRepository<LinesInvoice, Long>, JpaSpecificationExecutor<LinesInvoice> {

    List<LinesInvoice> findAllByInvoiceId(Long invoiceId);
}

package com.sigcon.backend.invoices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.invoices.domain.model.Invoices;

public interface InvoiceRepository extends JpaRepository<Invoices, Long>, JpaSpecificationExecutor<Invoices> {

    // @Query("SELECT i FROM invoices i WHERE i.type_nvoice.id = :typeInvoiceId AND i.company.id = :companyId")
    Invoices findByTypeInvoiceIdAndCompanyId(Long typeInvoiceId, Long companyId);
}

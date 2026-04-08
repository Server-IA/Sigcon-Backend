package com.sigcon.backend.invoices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.invoices.domain.model.TypesInvoices;

public interface TypeInvoiceRepository extends JpaRepository<TypesInvoices, Long> {

}

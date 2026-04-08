package com.sigcon.backend.invoices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.invoices.domain.model.InvoiceStates;


public interface InvoiceStateRepository extends JpaRepository<InvoiceStates, Long> {

}

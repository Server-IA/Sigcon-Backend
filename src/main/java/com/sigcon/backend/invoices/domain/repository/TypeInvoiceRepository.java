package com.sigcon.backend.invoices.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.invoices.domain.model.TypesInvoices;

public interface TypeInvoiceRepository extends JpaRepository<TypesInvoices, Long> {

    /**
     * Busca un tipo de factura por su codigo (ej. 'FC' = Factura de Compra,
     * 'NC' = Nota de Credito, 'ND' = Nota de Debito). Usado por el procesador
     * AAEF para resolver el id del tipo al mapear invoices Type=02.
     */
    Optional<TypesInvoices> findByCodeAndDeletedAtIsNull(String code);
}

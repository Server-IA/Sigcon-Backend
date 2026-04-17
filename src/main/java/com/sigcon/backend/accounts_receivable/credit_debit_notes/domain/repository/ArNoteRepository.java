package com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.model.ArCreditDebitNote;

/**
 * Repositorio JPA para la entidad {@link ArCreditDebitNote}.
 * Provee consultas para notas credito y debito de facturas de venta.
 */
public interface ArNoteRepository
        extends JpaRepository<ArCreditDebitNote, Long>, JpaSpecificationExecutor<ArCreditDebitNote> {

    /**
     * Verifica si ya existe una nota con el numero indicado (no eliminada).
     *
     * @param noteNumber numero de la nota
     * @return true si existe una nota con ese numero
     */
    boolean existsByNoteNumberAndDeletedAtIsNull(String noteNumber);

    /**
     * Obtiene todas las notas asociadas a una factura de venta.
     *
     * @param invoiceId identificador de la factura
     * @return lista de notas de la factura
     */
    List<ArCreditDebitNote> findByInvoiceIdAndDeletedAtIsNull(Long invoiceId);

    /**
     * Cuenta la cantidad de notas por tipo (CREDIT o DEBIT) en un año fiscal,
     * utilizado para generar el consecutivo.
     *
     * @param noteType tipo de nota (CREDIT o DEBIT)
     * @return cantidad de notas del tipo indicado
     */
    long countByNoteTypeAndDeletedAtIsNull(String noteType);
}

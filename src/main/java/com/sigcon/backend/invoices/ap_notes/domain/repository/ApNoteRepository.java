package com.sigcon.backend.invoices.ap_notes.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.invoices.ap_notes.domain.model.ApCreditDebitNote;

/**
 * Repositorio JPA para la entidad {@link ApCreditDebitNote}.
 * Provee consultas para notas credito y debito de facturas de compra.
 */
public interface ApNoteRepository extends JpaRepository<ApCreditDebitNote, Long>, JpaSpecificationExecutor<ApCreditDebitNote> {

    /**
     * Verifica si ya existe una nota con el numero indicado (no eliminada).
     *
     * @param noteNumber numero de la nota
     * @return true si existe una nota con ese numero
     */
    boolean existsByNoteNumberAndDeletedAtIsNull(String noteNumber);

    /**
     * Obtiene todas las notas asociadas a una factura especifica.
     *
     * @param invoiceId identificador de la factura
     * @return lista de notas de la factura
     */
    List<ApCreditDebitNote> findByInvoiceIdAndDeletedAtIsNull(Long invoiceId);

    /**
     * Cuenta la cantidad de notas por tipo (CREDIT o DEBIT) no eliminadas.
     * Utilizado para generar el consecutivo de notas.
     *
     * @param noteType tipo de nota (CREDIT o DEBIT)
     * @return cantidad de notas del tipo indicado
     */
    long countByNoteTypeAndDeletedAtIsNull(String noteType);
}

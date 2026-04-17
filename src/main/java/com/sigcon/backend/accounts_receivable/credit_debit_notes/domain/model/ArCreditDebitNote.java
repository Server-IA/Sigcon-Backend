package com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad que representa una nota credito o nota debito asociada a una factura de venta.
 * Cubre HU AR-07.
 * <ul>
 *   <li>Nota Credito (CREDIT): reduce el saldo pendiente de la factura
 *       (Debito Ingresos / Credito CxC).</li>
 *   <li>Nota Debito (DEBIT): incrementa el saldo pendiente de la factura
 *       (Debito CxC / Credito Ingresos).</li>
 * </ul>
 * Cada nota genera un asiento contable automaticamente y tiene un consecutivo
 * con formato NC-FV-{anio}{6d} o ND-FV-{anio}{6d}.
 *
 * @see SalesInvoice
 */
@Entity
@Table(name = "ar_credit_debit_notes")
@SQLDelete(sql = "UPDATE ar_credit_debit_notes SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArCreditDebitNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Factura de venta a la cual esta asociada la nota. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SalesInvoice invoice;

    /** Tipo de nota: CREDIT (nota credito) o DEBIT (nota debito). */
    @Column(name = "note_type", nullable = false, length = 10)
    private String noteType;

    /** Numero consecutivo de la nota (NC-FV-{anio}{6d} o ND-FV-{anio}{6d}). */
    @Column(name = "note_number", nullable = false, length = 30, unique = true)
    private String noteNumber;

    /** Valor monetario de la nota. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Razon o justificacion de la nota. */
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** ID del asiento contable generado por esta nota. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** ID del usuario que creo la nota. */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

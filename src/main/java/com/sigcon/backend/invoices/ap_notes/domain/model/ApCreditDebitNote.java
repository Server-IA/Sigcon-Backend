package com.sigcon.backend.invoices.ap_notes.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import com.sigcon.backend.invoices.domain.model.Invoices;

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
 * Entidad que representa una nota credito o nota debito asociada a una factura de compra.
 * <ul>
 *   <li>Nota Credito (CREDIT): reduce el saldo pendiente de la factura.</li>
 *   <li>Nota Debito (DEBIT): incrementa el saldo pendiente de la factura.</li>
 * </ul>
 * Cada nota genera un asiento contable automaticamente.
 *
 * @see Invoices
 */
@Entity
@Table(name = "ap_credit_debit_notes")
@SQLDelete(sql = "UPDATE ap_credit_debit_notes SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApCreditDebitNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Factura a la cual esta asociada la nota. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoices invoice;

    /** Tipo de nota: CREDIT (nota credito) o DEBIT (nota debito). */
    @Column(name = "note_type", nullable = false, length = 10)
    private String noteType;

    /** Numero consecutivo de la nota (NC-{anio}{seq} o ND-{anio}{seq}). */
    @Column(name = "note_number", nullable = false, length = 30)
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

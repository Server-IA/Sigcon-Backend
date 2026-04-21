package com.sigcon.backend.accounts_receivable.payments.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Entidad que representa un cobro o abono recibido sobre una factura de venta.
 * Cubre HUs AR-02 y AR-08.
 * Registra el monto recibido, metodo, cuenta bancaria, movimiento bancario asociado
 * y la vinculacion con el asiento contable generado automaticamente.
 *
 * @see SalesInvoice
 */
@Entity
@Table(name = "ar_payments")
@SQLDelete(sql = "UPDATE ar_payments SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Factura de venta a la cual se aplica el cobro. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SalesInvoice invoice;

    /** Monto del cobro o abono. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Fecha en que se recibio el cobro. */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Referencia unica del cobro (numero de transferencia, recibo, etc.). */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /** Metodo de pago utilizado (TRANSFERENCIA, EFECTIVO, CHEQUE, etc.). */
    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    /** ID de la cuenta bancaria destino del cobro (si aplica). */
    @Column(name = "bank_account_id")
    private Long bankAccountId;

    /** ID de la caja destino del cobro (si aplica). */
    @Column(name = "cash_id")
    private Long cashId;

    /** ID del movimiento bancario que origino el cobro. */
    @Column(name = "bank_movement_id")
    private Long bankMovementId;

    /** Estado del cobro (COMPLETED por defecto). */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    /** ID del asiento contable generado por este cobro. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** Observaciones adicionales del cobro. */
    @Column(name = "notes", length = 500)
    private String notes;

    /** ID del usuario que registro el cobro. */
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

    @jakarta.persistence.PrePersist
    protected void __onCreateTenant() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
    }

    @jakarta.persistence.PostLoad
    protected void __onLoadTenant() {
        if (com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin()) return;
        Long current = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (current == null || this.companyId == null) return;
        if (!current.equals(this.companyId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                    "Recurso fuera del tenant actual");
        }
    }
}

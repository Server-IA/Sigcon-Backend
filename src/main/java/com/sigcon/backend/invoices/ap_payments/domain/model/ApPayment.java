package com.sigcon.backend.invoices.ap_payments.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Entidad que representa un pago o abono realizado a una factura de compra.
 * Registra el monto pagado, referencia de pago, metodo de pago y
 * la vinculacion con el asiento contable generado automaticamente.
 *
 * @see Invoices
 */
@Entity
@Table(name = "ap_payments")
@SQLDelete(sql = "UPDATE ap_payments SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Factura a la cual se aplica el pago. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoices invoice;

    /** Monto del pago o abono. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Fecha en que se realizo el pago. */
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Referencia unica del pago (numero de transferencia, recibo, etc.). */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /** Metodo de pago utilizado (TRANSFERENCIA, EFECTIVO, CHEQUE, etc.). */
    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    /** ID de la cuenta bancaria origen del pago (si aplica). */
    @Column(name = "bank_account_id")
    private Long bankAccountId;

    /** ID de la caja origen del pago (si aplica). */
    @Column(name = "cash_id")
    private Long cashId;

    /** ID del cheque utilizado (si aplica). */
    @Column(name = "check_id")
    private Long checkId;

    /** Estado del pago (COMPLETED por defecto). */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    /** ID del asiento contable generado por este pago. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /**
     * AP-09: ID del movimiento financiero BNK conciliado con este pago.
     * Null si el pago aun no se ha conciliado con el extracto bancario.
     * Permite three-way match: ApPayment ↔ BankMovement ↔ (opcionalmente) GoodsReceipt via invoice.
     */
    @Column(name = "bank_movement_id")
    private Long bankMovementId;

    /** AP-09: Timestamp de conciliacion con movimiento bancario. */
    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    /** Observaciones adicionales del pago. */
    @Column(name = "notes", length = 500)
    private String notes;

    /** ID del usuario que registro el pago. */
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

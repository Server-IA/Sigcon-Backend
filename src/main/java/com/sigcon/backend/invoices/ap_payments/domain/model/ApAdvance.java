package com.sigcon.backend.invoices.ap_payments.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

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
 * Entidad que representa un anticipo entregado a un tercero (proveedor).
 * Un anticipo puede aplicarse posteriormente a una factura de compra,
 * reduciendo el saldo pendiente de la misma.
 *
 * @see ThirdParty
 */
@Entity
@Table(name = "ap_advances")
@SQLDelete(sql = "UPDATE ap_advances SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Tercero (proveedor) beneficiario del anticipo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id", nullable = false)
    private ThirdParty thirdParty;

    /** Monto total del anticipo. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Fecha del anticipo. */
    @Column(name = "advance_date", nullable = false)
    private LocalDate advanceDate;

    /** Referencia del anticipo (comprobante, transferencia, etc.). */
    @Column(name = "advance_reference", length = 100)
    private String advanceReference;

    /** Estado del anticipo: PENDING o APPLIED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** ID de la factura a la que se aplico el anticipo. */
    @Column(name = "applied_invoice_id")
    private Long appliedInvoiceId;

    /** Monto aplicado del anticipo a la factura. */
    @Column(name = "applied_amount", precision = 19, scale = 2)
    private BigDecimal appliedAmount;

    /** Fecha y hora en que se aplico el anticipo. */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /** ID de la cuenta bancaria origen (si aplica). */
    @Column(name = "bank_account_id")
    private Long bankAccountId;

    /** ID de la caja origen (si aplica). */
    @Column(name = "cash_id")
    private Long cashId;

    /** ID del asiento contable generado por este anticipo. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** Observaciones adicionales. */
    @Column(name = "notes", length = 500)
    private String notes;

    /** ID del usuario que registro el anticipo. */
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

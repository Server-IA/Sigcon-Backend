package com.sigcon.backend.invoices.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.ManyToAny;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.integration.domain.model.IntegrationSource;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;

import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "invoices")
@SQLDelete(sql = "UPDATE invoices SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class Invoices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_invoice_id", nullable = false)
    private TypesInvoices typeInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_state_id", nullable = false)
    private InvoiceStates invoiceState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_forms_id", nullable = false)
    private PaymentForms paymentForms;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id", nullable = true)
    private ThirdParty thirdParty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_reference_id", nullable = true)
    private Invoices invoiceReference;

    @Column(name = "resolution", nullable = false)
    private String resolution;

    @Column(name = "resolution_invoice", nullable = false)
    private String resolutionInvoice;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "invoice_due_day", nullable = false)
    @Min(value = 1, message = "El día de vencimiento debe ser mayor que 0")
    @Max(value = 31, message = "El día de vencimiento debe ser menor que 31")
    private Integer invoiceDueDay;

    @Column(name = "total_payment", nullable = false)
    private Double totalPayment;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Column(name = "total_discount", nullable = false)
    private Double totalDiscount;

    @Column(name = "total_tax", nullable = false)
    private Double totalTax;

    @Column(name = "invoice_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusesInvoices status;

    @Column(name = "supplier_invoice_number", length = 50)
    private String supplierInvoiceNumber;

    @Column(name = "balance_due", nullable = false)
    @Builder.Default
    private Double balanceDue = 0.0;

    /**
     * HU-AP-07: Porcentaje de descuento por pronto pago (ej. 2.0 = 2%).
     * Se aplica automaticamente en el pago si la fecha de pago es <= factura + earlyPaymentDiscountDays.
     */
    @Column(name = "early_payment_discount_pct", precision = 5)
    private Double earlyPaymentDiscountPct;

    /**
     * HU-AP-07: Dias de gracia para aplicar el descuento (desde la fecha de factura).
     */
    @Column(name = "early_payment_discount_days")
    private Integer earlyPaymentDiscountDays;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "notes", nullable = true)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    /**
     * Trazabilidad del origen del documento (MANUAL vs AAEF).
     * Campos: source, external_id, exchange_id (creados en V32).
     */
    @Embedded
    @Builder.Default
    private IntegrationSource integrationSource = IntegrationSource.builder().build();

    @PrePersist
    public void prePersist() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = StatusesInvoices.PENDING;
        this.totalPayment = 0.0;
        this.totalAmount = 0.0;
        this.totalDiscount = 0.0;
        this.totalTax = 0.0;
        if (this.balanceDue == null) {
            this.balanceDue = 0.0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
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

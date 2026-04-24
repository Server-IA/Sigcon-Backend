package com.sigcon.backend.accounts_receivable.sales_invoices.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.integration.domain.model.IntegrationSource;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad factura de venta (FV) del modulo Cuentas por Cobrar.
 * Cubre HUs: AR-01A, AR-01B, AR-04, AR-11, AR-13.
 * Soporta moneda extranjera con tasa de cambio, calculo de impuestos
 * y retenciones, y numeracion consecutiva por año fiscal.
 */
@Entity
@Table(name = "sales_invoices")
@SQLDelete(sql = "UPDATE sales_invoices SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Numero consecutivo de la factura en formato FV-{año}{6 digitos} */
    @Column(name = "invoice_number", nullable = false, length = 30)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id", nullable = false)
    private ThirdParty thirdParty;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = true)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private CurrencyType currency;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_form_id", nullable = true)
    private PaymentForms paymentForm;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total_tax", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalTax = BigDecimal.ZERO;

    @Column(name = "total_withholding", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalWithholding = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "balance_due", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SalesInvoiceStatus status = SalesInvoiceStatus.DRAFT;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** Numero de resolucion DIAN (placeholder para facturacion electronica) */
    @Column(name = "resolution_number", length = 100)
    private String resolutionNumber;

    /** CUFE - Codigo Unico de Factura Electronica (null hasta integracion DIAN) */
    @Column(name = "cufe", length = 200)
    private String cufe;

    /**
     * HU-AR-01A E6: Estado DIAN de la factura electronica
     * (PENDING/SENT/ACCEPTED/REJECTED/VOIDED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dian_status", length = 20)
    @Builder.Default
    private DianStatus dianStatus = DianStatus.PENDING;

    /** Mensaje de respuesta DIAN (en caso de REJECTED). */
    @Column(name = "dian_message", length = 500)
    private String dianMessage;

    @Column(name = "xml_sent", nullable = false)
    @Builder.Default
    private Boolean xmlSent = false;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Trazabilidad del origen del documento (MANUAL vs AAEF).
     * Campos: source, external_id, exchange_id (creados en V32).
     */
    @Embedded
    @Builder.Default
    private IntegrationSource integrationSource = IntegrationSource.builder().build();

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesInvoiceLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.exchangeRate == null) this.exchangeRate = BigDecimal.ONE;
        if (this.status == null) this.status = SalesInvoiceStatus.DRAFT;
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

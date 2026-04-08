package com.sigcon.backend.invoices.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.ManyToAny;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyLocation;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

import jakarta.persistence.Column;
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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class Invoices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_origin_id", nullable = true)
    private CompanyLocation locationOrigin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_destination_id", nullable = true)
    private CompanyLocation locationDestination;

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

    @Column(name = "notes", nullable = true)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = StatusesInvoices.PENDING;
        this.totalPayment = 0.0;
        this.totalAmount = 0.0;
        this.totalDiscount = 0.0;
        this.totalTax = 0.0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}

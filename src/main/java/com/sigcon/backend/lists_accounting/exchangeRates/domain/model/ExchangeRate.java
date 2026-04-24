package com.sigcon.backend.lists_accounting.exchangeRates.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.ExchangeType;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.StatusCurrencyExchange;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;

@Entity
@Table(name = "exchange_rates")
@SQLDelete(sql = "UPDATE exchange_rates SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private CurrencyType currencyExchange;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_iso", nullable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private CurrencyType currencyExchanged;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange_type", nullable = false)
    private ExchangeType exchangeType;

    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusCurrencyExchange status;

    @Column(name = "created_at", nullable = false)    
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PrePersist
    public void prePersist() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = StatusCurrencyExchange.ACTIVE;
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
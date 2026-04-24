package com.sigcon.backend.lists_accounting.accounting_account.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;


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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounting_accounts")
@SQLDelete(sql = "UPDATE accounting_accounts SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class AccountingAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Multi-tenant (V10-B). Auto-inyectado en @PrePersist. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puc_id", nullable = false)
    private ChartOfAccount pucAccount;

    @Column(name = "custom_name", length = 50, nullable = false)
    private String customName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_type_id", nullable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private CurrencyType currencyType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id", nullable = true)
    private CostCenter costCenter;

    @Column(name = "tax_rule_id", nullable = true)
    private Long taxRuleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false)
    private AccountNature nature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = AccountStatus.ACTIVE;
        if (this.companyId == null) {
            this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        }
    }

    @jakarta.persistence.PostLoad
    protected void onLoad() {
        if (com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin()) return;
        Long current = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (current == null || this.companyId == null) return;
        if (!current.equals(this.companyId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                    "Recurso fuera del tenant actual");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

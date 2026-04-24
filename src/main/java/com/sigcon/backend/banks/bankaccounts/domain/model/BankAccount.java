package com.sigcon.backend.banks.bankaccounts.domain.model;

import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus;
import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountType;
import com.sigcon.backend.banks.banks.domain.model.Bank;
import com.sigcon.backend.banks.banks.domain.model.BankBranch;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_accounts")
@SQLDelete(sql = "UPDATE bank_accounts SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private BankAccountType accountType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_type_id", nullable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private CurrencyType currencyType;

    @Column(name = "initial_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal initialBalance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_account_id", nullable = false)
    private AccountingAccount accountingAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_branch_id")
    private BankBranch bankBranch;

    @Column(name = "account_executive", length = 100)
    private String accountExecutive;

    @Column(name = "bank_phone", length = 20)
    private String bankPhone;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Column(name = "allows_overdraft", nullable = false)
    @Builder.Default
    private Boolean allowsOverdraft = false;

    @Column(name = "credit_limit", precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "notify_low_balance", nullable = false)
    @Builder.Default
    private Boolean notifyLowBalance = false;

    @Column(name = "minimum_balance", precision = 15, scale = 2)
    private BigDecimal minimumBalance;

    @Column(name = "handles_checkbook", nullable = false)
    @Builder.Default
    private Boolean handlesCheckbook = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BankAccountStatus status = BankAccountStatus.ACTIVA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "closing_date")
    private LocalDate closingDate;

    @Column(name = "last_reconciliation_date")
    private LocalDate lastReconciliationDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = BankAccountStatus.ACTIVA;
        }
        if (this.handlesCheckbook == null) {
            this.handlesCheckbook = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
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

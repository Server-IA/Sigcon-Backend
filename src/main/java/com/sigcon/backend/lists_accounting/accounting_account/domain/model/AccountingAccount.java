package com.sigcon.backend.lists_accounting.accounting_account.domain.model;

import java.time.LocalDateTime;

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;

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

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class AccountingAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puc_id", nullable = false)
    private ChartOfAccount puc;

    @Column(name = "custom_name", length = 50, nullable = false, unique = true)
    private String customName;

    @Column(name = "base_currency", length = 50, nullable = false)
    private String baseCurrency;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "cost_center_id", nullable = true)
//    private Object costCenter; // Stub - reemplazar con entidad real cuando exista

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "depreciation_rule_id", nullable = true)
//    private Object depreciationRule; // Stub - reemplazar con entidad real cuando exista

    // Cambia la relación por un campo básico
    @Column(name = "cost_center_id")
    private Long costCenterId; 

    @Column(name = "depreciation_rule_id")
    private Long depreciationRuleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false)
    private AccountNature nature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

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
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

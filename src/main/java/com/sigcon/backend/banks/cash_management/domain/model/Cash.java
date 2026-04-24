package com.sigcon.backend.banks.cash_management.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.banks.cash_management.domain.model.enums.AccountingBook;
import com.sigcon.backend.banks.cash_management.domain.model.enums.AuditFrequency;
import com.sigcon.backend.banks.cash_management.domain.model.enums.CashStatus;
import com.sigcon.backend.banks.cash_management.domain.model.enums.CashType;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cash")
@SQLDelete(sql = "UPDATE cash SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cash {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(name = "cash_code", nullable = false, length = 20)
    @NotBlank(message = "El codigo de la caja es obligatorio")
    private String cashCode;

    @Column(name = "cash_name", nullable = false, length = 100)
    @NotBlank(message = "El nombre de la caja es obligatorio")
    private String cashName;

    @Enumerated(EnumType.STRING)
    @Column(name = "cash_type", nullable = false)
    @NotNull(message = "El tipo de caja es obligatorio")
    private CashType cashType;

    @Enumerated(EnumType.STRING)
    @Column(name = "cash_status", nullable = false)
    @Builder.Default
    private CashStatus cashStatus = CashStatus.ACTIVE;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "physical_location", nullable = false, length = 200)
    @NotBlank(message = "La ubicación física es obligatoria")
    private String physicalLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "principal_responsible_id", nullable = false)
    @NotNull(message = "El responsable principal es obligatorio")
    private User principalResponsible;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternate_responsible_id", nullable = true)
    private User alternateResponsible;

    @Column(name = "operation_schedule", length = 20)
    private String operationSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    @NotNull(message = "La moneda es obligatoria")
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private CurrencyType currency;

    @Column(name = "initial_balance", nullable = false, precision = 15, scale = 2)
    @NotNull(message = "El saldo inicial es obligatorio")
    @DecimalMin(value = "0.0", message = "El saldo inicial no puede ser negativo")
    private BigDecimal initialBalance;

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    @NotNull(message = "El saldo actual es obligatorio")
    private BigDecimal currentBalance;

    @Column(name = "initial_balance_date", nullable = false)
    @NotNull(message = "La fecha del saldo inicial es obligatoria")
    private LocalDate initialBalanceDate;

    @Column(name = "cash_creation_date", nullable = false)
    @NotNull(message = "La fecha de creación de la caja es obligatoria")
    private LocalDate cashCreationDate; 

     @Column(name = "max_limit", precision = 15, scale = 2)
    private BigDecimal maxLimit;

    @Column(name = "min_limit", precision = 15, scale = 2)
    private BigDecimal minLimit;

    @Column(name = "requires_authorization", nullable = false)
    @NotNull(message = "El campo requiere autorización es obligatorio")
    private Boolean requiresAuthorization;

    @Column(name = "max_amount_without_authorization", precision = 15, scale = 2)
    private BigDecimal maxAmountWithoutAuthorization;

    @Column(name = "notify_limit", precision = 15, scale = 2)
    private BigDecimal notifyLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_frequency", nullable = false)
    @NotNull(message = "La periodicidad del arqueo es obligatoria")
    private AuditFrequency auditFrequency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_account_id", nullable = false)
    @NotNull(message = "La cuenta contable es obligatoria")
    private AccountingAccount accountingAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id", nullable = true)
    private CostCenter costCenter;

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_book", nullable = false)
    @NotNull(message = "El libro contable es obligatorio")
    private AccountingBook accountingBook;

    @Column(name = "closing_date")
    private LocalDate closingDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // El saldo actual se inicializa igual al saldo inicial (BNK-RF-10 postcondiciones)
        if (this.currentBalance == null && this.initialBalance != null) {
            this.currentBalance = this.initialBalance;
        }
        // El estado se inicializa como ACTIVE por defecto (BNK-RF-10 postcondiciones)
        if (this.cashStatus == null) {
            this.cashStatus = CashStatus.ACTIVE;
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

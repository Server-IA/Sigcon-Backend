package com.sigcon.backend.lists_accounting.depretation_rules.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

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
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "depretation_rules")
@SQLDelete(sql = "UPDATE depretation_rules SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepretationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(nullable = false, length = 100)
    @NotBlank(message = "El nombre es obligatorio")
    private String name; 

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "depretation_type")
    @NotNull(message = "El tipo de depreciación es obligatorio")
    private DepretationType depretationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_account_id", nullable = false)
    @NotNull(message = "La cuenta contable es obligatoria")
    private AccountingAccount accountingAccount;

    @Column(nullable = false, precision = 5, scale = 2)
    @NotNull(message = "La tasa de depreciación es obligatoria")
    private BigDecimal depretationRate;

    @Column(nullable = false)
    @NotNull(message = "La vida util es obligatoria")
    private Integer usefulLifeYears;

    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull(message = "El valor residual es obligatorio")
    private BigDecimal residualValue;

    @NotNull(message = "La fecha de vigencia es obligatoria")
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    //Descripcion estructurada como texto largo
    @Column(columnDefinition = "TEXT", nullable = false)
    @NotNull(message = "La descripción estructurada es obligatoria")
    private String descriptionStructured;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DepretationStatus status = DepretationStatus.ACTIVE; 

    // Parte de la auditoria (comnetada porque no se si esta bien)
    /* 
    @Column(nullable = false, name = "created_by_id")
    @NotNull(message = "El usuario creador es obligatorio")
    private Long createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
    private User createdBy;
    */ 

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    } 

    @PreRemove
    protected void onDelete() {
        this.deletedAt = LocalDateTime.now();
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

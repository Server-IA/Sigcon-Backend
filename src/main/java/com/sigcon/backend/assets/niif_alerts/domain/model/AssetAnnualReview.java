package com.sigcon.backend.assets.niif_alerts.domain.model;

import com.sigcon.backend.assets.assets.domain.model.Assets;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

/**
 * HU-ACT-12: Entidad para revisiones anuales de activos fijos segun NIC 16.
 * Registra la revision anual de vida util y valor residual de cada activo,
 * almacenando valores anteriores y nuevos para trazabilidad.
 */
@Entity
@Table(name = "asset_annual_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asset_fiscal_year",
                columnNames = {"asset_id", "fiscal_year"}
        ))
@SQLDelete(sql = "UPDATE asset_annual_reviews SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetAnnualReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Assets asset;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "previous_useful_life")
    private Integer previousUsefulLife;

    @Column(name = "new_useful_life")
    private Integer newUsefulLife;

    @Column(name = "previous_residual_value", precision = 19, scale = 2)
    private BigDecimal previousResidualValue;

    @Column(name = "new_residual_value", precision = 19, scale = 2)
    private BigDecimal newResidualValue;

    @Column(name = "previous_depreciation_monthly", precision = 19, scale = 2)
    private BigDecimal previousDepreciationMonthly;

    @Column(name = "new_depreciation_monthly", precision = 19, scale = 2)
    private BigDecimal newDepreciationMonthly;

    /**
     * Tipo de revision: CONFIRMED (sin cambios), USEFUL_LIFE_CHANGE,
     * RESIDUAL_VALUE_CHANGE.
     */
    @Column(name = "review_type", nullable = false, length = 30)
    private String reviewType;

    @Column(name = "justification", length = 500)
    private String justification;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

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

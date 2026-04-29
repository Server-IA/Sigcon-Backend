package com.sigcon.backend.general.accounting.series.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HU-CG-03A E3/E5: configuracion de series de consecutivos por tipo de
 * comprobante contable. Cada empresa puede definir su propio rango y prefijo
 * para diferenciar, por ejemplo, JE (general), AJ (ajustes), CI (cierre),
 * REV (reversion), etc.
 *
 * <p>Multi-tenant: companyId NOT NULL + UNIQUE compuesto (companyId, voucherType)
 * que permite a distintas empresas tener un 'JE' independiente.</p>
 *
 * <p>El sistema notifica via UI cuando current_number / end_number >= alert_threshold_pct.
 * Cuando current_number alcanza end_number, status pasa a EXHAUSTED y bloquea
 * nuevas asignaciones — el admin debe ampliar el rango o crear una serie nueva.</p>
 */
@Entity
@Table(name = "voucher_series_config")
@SQLDelete(sql = "UPDATE voucher_series_config SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoucherSeriesConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Codigo del tipo de comprobante. JE=general, AJ=ajuste, CI=cierre, etc. */
    @Column(name = "voucher_type", nullable = false, length = 20)
    private String voucherType;

    /** Prefijo legible que se antepone al numero (ej. "JE", "AJ", "CI"). */
    @Column(name = "prefix", nullable = false, length = 20)
    private String prefix;

    @Column(name = "start_number", nullable = false)
    private Long startNumber;

    @Column(name = "end_number", nullable = false)
    private Long endNumber;

    @Column(name = "current_number", nullable = false)
    private Long currentNumber;

    /** Porcentaje (0-100) a partir del cual se notifica al admin. Default 80. */
    @Column(name = "alert_threshold_pct", nullable = false)
    private Integer alertThresholdPct;

    @Column(name = "description")
    private String description;

    /** ACTIVE | INACTIVE | EXHAUSTED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @jakarta.persistence.PrePersist
    protected void __onCreateTenant() {
        if (this.companyId == null) {
            this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        }
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

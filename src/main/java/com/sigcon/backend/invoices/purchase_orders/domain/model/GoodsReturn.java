package com.sigcon.backend.invoices.purchase_orders.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QA-BLOQUE-AY HU-AP-21 (2026-05-05): cabecera de devolucion de mercancia.
 * Se asocia a una recepcion previa y permite detallar cantidades devueltas
 * por linea, soportando devoluciones parciales (no solo rechazo total).
 */
@Entity
@Table(name = "goods_returns")
@SQLDelete(sql = "UPDATE goods_returns SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoodsReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Codigo unico DV-{año}{secuencial-6}. */
    @Column(name = "return_number", nullable = false, length = 30)
    private String returnNumber;

    /** Recepcion sobre la cual se devuelven items. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private GoodsReceipt receipt;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "goodsReturn", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GoodsReturnLine> lines = new ArrayList<>();

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

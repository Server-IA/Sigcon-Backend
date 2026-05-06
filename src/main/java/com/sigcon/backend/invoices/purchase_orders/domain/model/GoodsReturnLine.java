package com.sigcon.backend.invoices.purchase_orders.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QA-BLOQUE-AY HU-AP-21 (2026-05-05): linea de detalle de una devolucion.
 * Cada linea referencia una linea de la recepcion original y la cantidad
 * que se devuelve. Permite devolver una fraccion de lo recibido.
 */
@Entity
@Table(name = "goods_return_lines")
@SQLDelete(sql = "UPDATE goods_return_lines SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoodsReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_return_id", nullable = false)
    private GoodsReturn goodsReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_receipt_line_id", nullable = false)
    private GoodsReceiptLine goodsReceiptLine;

    @Column(name = "quantity_returned", nullable = false, precision = 19, scale = 2)
    private BigDecimal quantityReturned;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @jakarta.persistence.PrePersist
    protected void __onCreateTenant() {
        if (this.companyId == null) {
            if (this.goodsReturn != null && this.goodsReturn.getCompanyId() != null) {
                this.companyId = this.goodsReturn.getCompanyId();
            } else {
                this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
            }
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

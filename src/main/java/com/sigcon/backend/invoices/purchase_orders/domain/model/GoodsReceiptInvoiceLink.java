package com.sigcon.backend.invoices.purchase_orders.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.platform.tenant.TenantContext;
import com.sigcon.backend.platform.tenant.TenantIsolationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QA-BLOQUE-AY HU-AP-19 E1/E4/E5/E6 (2026-05-06): vinculacion parcial N:M entre
 * recepciones (goods_receipts) y facturas (invoices) con monto facturado por
 * link. Permite:
 * <ul>
 *   <li>E1: una recepcion vinculada a varias facturas</li>
 *   <li>E4: bloquear nuevo link si la recepcion ya esta totalmente facturada</li>
 *   <li>E5: factura inferior al monto recibido -> link aceptado, saldo abierto</li>
 *   <li>E6: facturar la recepcion en partes</li>
 * </ul>
 */
@Entity
@Table(name = "goods_receipt_invoice_links")
@SQLDelete(sql = "UPDATE goods_receipt_invoice_links SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptInvoiceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "receipt_id", nullable = false)
    private Long receiptId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "invoiced_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal invoicedAmount;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (companyId == null) {
            Long tenant = TenantContext.getCompanyId();
            if (tenant != null) companyId = tenant;
        }
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    @PostLoad
    private void enforceTenant() {
        Long ctx = TenantContext.getCompanyId();
        if (ctx != null && companyId != null && !ctx.equals(companyId)
                && !TenantContext.isPlatformAdmin()) {
            throw new TenantIsolationException(
                "Cross-tenant access blocked on GoodsReceiptInvoiceLink id=" + id);
        }
    }
}

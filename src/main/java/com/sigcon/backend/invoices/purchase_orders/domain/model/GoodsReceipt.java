package com.sigcon.backend.invoices.purchase_orders.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
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
 * Entidad que representa una recepcion de bienes o servicios asociada a una orden de compra.
 * Registra las cantidades efectivamente recibidas y permite vincular con una factura
 * de compra para el three-way match (OC - Recepcion - Factura).
 *
 * @see PurchaseOrder
 * @see GoodsReceiptLine
 */
@Entity
@Table(name = "goods_receipts")
@SQLDelete(sql = "UPDATE goods_receipts SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Orden de compra asociada a esta recepcion. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    /** Numero consecutivo de la recepcion (RC-{anio}{secuencia}). */
    @Column(name = "receipt_number", nullable = false, length = 30)
    private String receiptNumber;

    /** Fecha en que se realizo la recepcion. */
    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    /** Estado de la recepcion (RECEIVED por defecto). Valores: RECEIVED, REJECTED, RETURNED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "RECEIVED";

    /** ID de la factura vinculada para three-way match. */
    @Column(name = "invoice_id")
    private Long invoiceId;

    /** Observaciones de la recepcion. */
    @Column(name = "notes", length = 500)
    private String notes;

    /** ID del usuario que registro la recepcion. */
    @Column(name = "created_by")
    private Long createdBy;

    /** AP-22: Fecha de rechazo/devolucion. Null si no se rechazo. */
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    /** AP-22: Usuario que realizo el rechazo/devolucion. */
    @Column(name = "rejected_by")
    private Long rejectedBy;

    /** AP-22: Motivo del rechazo/devolucion (obligatorio, min 20 chars). */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Lineas de detalle de la recepcion. */
    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GoodsReceiptLine> lines = new ArrayList<>();

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

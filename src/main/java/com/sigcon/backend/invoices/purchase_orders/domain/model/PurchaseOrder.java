package com.sigcon.backend.invoices.purchase_orders.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

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
 * Entidad que representa una orden de compra en el modulo Cuentas por Pagar.
 * Sigue el flujo de aprobacion: DRAFT -> PENDING -> APPROVED/REJECTED -> CLOSED.
 * Una vez aprobada, permite la creacion de recepciones de bienes ({@link GoodsReceipt}).
 *
 * @see PurchaseOrderLine
 * @see GoodsReceipt
 */
@Entity
@Table(name = "purchase_orders")
@SQLDelete(sql = "UPDATE purchase_orders SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numero consecutivo de la orden de compra (OC-{anio}{secuencia}). */
    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    /** Proveedor al que se le emite la orden de compra. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id", nullable = false)
    private ThirdParty thirdParty;

    /** Fecha de emision de la orden. */
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    /** Fecha estimada de entrega. */
    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    /** Estado del flujo de aprobacion: DRAFT, PENDING, APPROVED, REJECTED, CLOSED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    /** Monto total de la orden (suma de las lineas). */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Observaciones generales de la orden. */
    @Column(name = "notes", length = 500)
    private String notes;

    /** ID del usuario que aprobo la orden. */
    @Column(name = "approved_by")
    private Long approvedBy;

    /** Fecha y hora de aprobacion. */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Razon del rechazo (si aplica). */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** ID del usuario que creo la orden. */
    @Column(name = "created_by")
    private Long createdBy;

    /** Lineas de detalle de la orden de compra. */
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

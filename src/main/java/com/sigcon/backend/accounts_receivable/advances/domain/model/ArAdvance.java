package com.sigcon.backend.accounts_receivable.advances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

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
 * Entidad que representa un anticipo recibido de un cliente.
 * Cubre HU AR-09.
 * Un anticipo puede aplicarse posteriormente a una o varias facturas de venta,
 * reduciendo el saldo pendiente. Estados posibles:
 * <ul>
 *   <li>PENDING: anticipo disponible sin aplicar</li>
 *   <li>PARTIALLY_APPLIED: parte del anticipo ya fue aplicada</li>
 *   <li>FULLY_APPLIED: anticipo totalmente aplicado</li>
 * </ul>
 *
 * @see ThirdParty
 */
@Entity
@Table(name = "ar_advances")
@SQLDelete(sql = "UPDATE ar_advances SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Tercero (cliente) emisor del anticipo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "third_party_id", nullable = false)
    private ThirdParty thirdParty;

    /** Monto total del anticipo recibido. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Monto aplicado acumulado del anticipo. */
    @Column(name = "applied_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    /** Fecha del anticipo. */
    @Column(name = "advance_date", nullable = false)
    private LocalDate advanceDate;

    /** Referencia del anticipo (comprobante, transferencia, etc.). */
    @Column(name = "advance_reference", length = 100)
    private String advanceReference;

    /** Estado del anticipo: PENDING, PARTIALLY_APPLIED o FULLY_APPLIED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** ID del movimiento bancario que origino el anticipo. */
    @Column(name = "bank_movement_id")
    private Long bankMovementId;

    /** ID de la cuenta bancaria destino (si aplica). */
    @Column(name = "bank_account_id")
    private Long bankAccountId;

    /** ID de la caja destino (si aplica). */
    @Column(name = "cash_id")
    private Long cashId;

    /** Fecha y hora de la ultima aplicacion del anticipo. */
    @Column(name = "last_applied_at")
    private LocalDateTime lastAppliedAt;

    /** ID del asiento contable generado al registrar el anticipo. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** Observaciones adicionales. */
    @Column(name = "notes", length = 500)
    private String notes;

    /** ID del usuario que registro el anticipo. */
    @Column(name = "created_by")
    private Long createdBy;

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

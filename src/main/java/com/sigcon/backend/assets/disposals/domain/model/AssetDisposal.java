package com.sigcon.backend.assets.disposals.domain.model;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.disposals.domain.model.enums.DisposalType;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad que registra las bajas y transferencias de activos fijos.
 * ACT-03: Cada disposicion genera opcionalmente un asiento contable
 * para reflejar la ganancia o perdida patrimonial.
 */
@Entity
@Table(name = "asset_disposals")
@SQLDelete(sql = "UPDATE asset_disposals SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDisposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Activo objeto de la baja o transferencia. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Assets asset;

    /** Tipo de disposicion: BAJA o TRANSFERENCIA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "disposal_type", nullable = false, length = 20)
    private DisposalType disposalType;

    /** Fecha en que se realiza la baja o transferencia. */
    @Column(name = "disposal_date", nullable = false)
    private LocalDate disposalDate;

    /**
     * Monto de enajenacion (precio de venta).
     * Aplica solo para tipo BAJA; en TRANSFERENCIA es null.
     */
    @Column(name = "disposal_amount", precision = 19, scale = 2)
    private BigDecimal disposalAmount;

    /** Valor en libros del activo al momento de la disposicion. */
    @Column(name = "book_value_at_disposal", nullable = false, precision = 19, scale = 2)
    private BigDecimal bookValueAtDisposal;

    /**
     * Ganancia o perdida calculada.
     * Para BAJA: disposalAmount - bookValueAtDisposal.
     * Para TRANSFERENCIA: siempre cero.
     */
    @Column(name = "gain_loss", nullable = false, precision = 19, scale = 2)
    private BigDecimal gainLoss;

    /** Motivo de la baja o transferencia. */
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    /** Informacion del destino (area, entidad). Solo para TRANSFERENCIA. */
    @Column(name = "destination_info", length = 500)
    private String destinationInfo;

    /** Referencia al asiento contable generado (puede ser null si fallo la creacion). */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** Usuario que registro la operacion. */
    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Marca de eliminacion logica. */
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

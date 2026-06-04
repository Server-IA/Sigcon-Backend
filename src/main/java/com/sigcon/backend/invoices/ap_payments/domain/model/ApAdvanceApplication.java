package com.sigcon.backend.invoices.ap_payments.domain.model;

import java.math.BigDecimal;
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
 * AP-RF-05 E6/E7 (Bloque DV, 2026-06-04): aplicacion individual de un anticipo
 * a UNA factura. Un anticipo puede aplicarse a varias facturas (cada aplicacion
 * = una fila aqui), y cada aplicacion puede revertirse de forma independiente,
 * restaurando el saldo de la factura destino y la disponibilidad del anticipo.
 *
 * La tabla la crea Hibernate ddl-auto (entity-driven), igual que otras entidades
 * recientes. Multi-tenant: company_id NOT NULL + @Filter + @PrePersist + @PostLoad.
 */
@Entity
@Table(name = "ap_advance_applications")
@SQLDelete(sql = "UPDATE ap_advance_applications SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApAdvanceApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Multi-tenant. Auto-inyectado en @PrePersist. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Anticipo al que pertenece esta aplicacion. */
    @Column(name = "advance_id", nullable = false)
    private Long advanceId;

    /** Factura de compra destino de la aplicacion. */
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    /** Monto aplicado del anticipo a la factura. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Asiento contable de la aplicacion (Debito CxP / Credito Anticipos). */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** Estado de la aplicacion: ACTIVE | REVERSED. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "applied_by")
    private Long appliedBy;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reverse_reason", length = 500)
    private String reverseReason;

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

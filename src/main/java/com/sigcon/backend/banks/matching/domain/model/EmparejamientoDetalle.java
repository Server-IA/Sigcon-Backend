package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BNK-HU-069 / BNK-HU-070: una fila por cada movimiento involucrado en un
 * emparejamiento, indicando de qué lado está (EXTRACTO o LIBROS) y su monto.
 * Permite N:M. Multi-tenant.
 */
@Entity
@Table(name = "emparejamiento_detalle")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmparejamientoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "emparejamiento_id", nullable = false)
    private Long emparejamientoId;

    @Column(name = "financial_movement_id", nullable = false)
    private Long financialMovementId;

    /** EXTRACTO | LIBROS */
    @Column(name = "lado", nullable = false, length = 8)
    private String lado;

    @Column(name = "monto", nullable = false, precision = 20, scale = 2)
    private BigDecimal monto;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (companyId == null) companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @jakarta.persistence.PostLoad
    protected void __onLoadTenant() {
        if (com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin()) return;
        Long current = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (current == null || this.companyId == null) return;
        if (!current.equals(this.companyId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException("Recurso fuera del tenant actual");
        }
    }
}

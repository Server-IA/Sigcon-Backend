package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BNK-HU-069 / BNK-HU-070: cabecera de un emparejamiento entre movimientos del
 * extracto y de libros. Soporta 1:1, N:1, 1:N y N:M (el detalle vive en
 * {@link EmparejamientoDetalle}). Multi-tenant.
 */
@Entity
@Table(name = "emparejamientos")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Emparejamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "reconciliation_session_id")
    private Long reconciliationSessionId;

    /** UNO_A_UNO | N_A_UNO | UNO_A_N | N_A_N */
    @Column(name = "tipo_emparejamiento", nullable = false, length = 16)
    private String tipoEmparejamiento;

    /** AUTOMATICO_EXACTO | AUTOMATICO_ALTO | AUTOMATICO_MEDIO | AUTOMATICO_AGREGADO | MANUAL */
    @Column(name = "metodo", nullable = false, length = 24)
    private String metodo;

    @Column(name = "score", nullable = false)
    @Builder.Default
    private Integer score = 0;

    /** CONFIRMADO | PROPUESTO | AMBIGUO | DESHECHO */
    @Column(name = "estado", nullable = false, length = 16)
    private String estado;

    @Column(name = "suma_extracto", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal sumaExtracto = BigDecimal.ZERO;

    @Column(name = "suma_libros", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal sumaLibros = BigDecimal.ZERO;

    @Column(name = "diferencia", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal diferencia = BigDecimal.ZERO;

    @Column(name = "motivo_match_manual", length = 500)
    private String motivoMatchManual;

    /** Snapshot legible de los parámetros usados en la corrida (HU-069 E9). */
    @Column(name = "parametros_usados", length = 1000)
    private String parametrosUsados;

    @Column(name = "confirmado_at")
    private LocalDateTime confirmadoAt;

    @Column(name = "confirmado_by", length = 120)
    private String confirmadoBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void prePersist() {
        if (companyId == null) companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (score == null) score = 0;
        if (sumaExtracto == null) sumaExtracto = BigDecimal.ZERO;
        if (sumaLibros == null) sumaLibros = BigDecimal.ZERO;
        if (diferencia == null) diferencia = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

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

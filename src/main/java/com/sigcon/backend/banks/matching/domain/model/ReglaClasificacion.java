package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BNK-HU-071: regla de clasificación que el pre-procesamiento aplica para
 * clasificar movimientos del extracto. Multi-tenant.
 */
@Entity
@Table(name = "reglas_clasificacion")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReglaClasificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    /** 1..999 ASC = se evalúa primero. */
    @Column(name = "prioridad", nullable = false)
    @Builder.Default
    private Integer prioridad = 100;

    @Column(name = "patron_regex", nullable = false, length = 500)
    private String patronRegex;

    /** DEBITO | CREDITO | CUALQUIERA */
    @Column(name = "signo", nullable = false, length = 12)
    @Builder.Default
    private String signo = "CUALQUIERA";

    @Column(name = "monto_min", precision = 20, scale = 2)
    private BigDecimal montoMin;

    @Column(name = "monto_max", precision = 20, scale = 2)
    private BigDecimal montoMax;

    @Column(name = "tipo_movimiento", nullable = false, length = 40)
    private String tipoMovimiento;

    @Column(name = "cuenta_puc_sugerida", length = 20)
    private String cuentaPucSugerida;

    /** GLOBAL | BANCO | CUENTA */
    @Column(name = "alcance", nullable = false, length = 12)
    @Builder.Default
    private String alcance = "GLOBAL";

    @Column(name = "banco_id")
    private Long bancoId;

    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "activa", nullable = false)
    @Builder.Default
    private Boolean activa = true;

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
        if (prioridad == null) prioridad = 100;
        if (signo == null) signo = "CUALQUIERA";
        if (alcance == null) alcance = "GLOBAL";
        if (activa == null) activa = true;
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

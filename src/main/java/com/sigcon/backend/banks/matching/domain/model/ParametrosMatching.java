package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BNK-HU-072: tolerancias y umbrales del motor de matching. Una fila global por
 * empresa (cuentaBancariaId NULL) y opcionalmente overrides por cuenta.
 */
@Entity
@Table(name = "parametros_matching")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametrosMatching {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** NULL = parámetros globales de la empresa. */
    @Column(name = "cuenta_bancaria_id")
    private Long cuentaBancariaId;

    @Column(name = "tolerancia_monto_abs", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal toleranciaMontoAbs = new BigDecimal("0.01");

    @Column(name = "tolerancia_monto_pct", nullable = false, precision = 6, scale = 3)
    @Builder.Default
    private BigDecimal toleranciaMontoPct = BigDecimal.ZERO;

    @Column(name = "tolerancia_fecha_dias", nullable = false)
    @Builder.Default
    private Integer toleranciaFechaDias = 2;

    @Column(name = "umbral_score_auto_aprobar", nullable = false)
    @Builder.Default
    private Integer umbralScoreAutoAprobar = 95;

    @Column(name = "umbral_score_sugerir", nullable = false)
    @Builder.Default
    private Integer umbralScoreSugerir = 60;

    @Column(name = "permitir_n_a_m", nullable = false)
    @Builder.Default
    private Boolean permitirNaM = true;

    @Column(name = "peso_monto", nullable = false)
    @Builder.Default
    private Integer pesoMonto = 50;

    @Column(name = "peso_fecha", nullable = false)
    @Builder.Default
    private Integer pesoFecha = 30;

    @Column(name = "peso_texto", nullable = false)
    @Builder.Default
    private Integer pesoTexto = 15;

    @Column(name = "peso_referencia", nullable = false)
    @Builder.Default
    private Integer pesoReferencia = 5;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (companyId == null) companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
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

package com.sigcon.backend.banks.trm.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BNK-HU-076 E1: TRM (Tasa Representativa del Mercado) histórica por día y moneda.
 *
 * <p>La TRM oficial la publica la Superintendencia Financiera de Colombia. Esta tabla
 * conserva el valor en COP por unidad de la moneda extranjera para cada fecha, con la
 * fuente del dato (MANUAL cargada por el contador, OFICIAL si vino de la Super, o
 * ULTIMA_PUBLICADA si el job la propagó por falta de dato del día).
 *
 * <p>El fetch automático al servicio oficial de la Super es infraestructura externa
 * (diferido); el sistema soporta carga manual + un job que arrastra la última TRM
 * publicada cuando no hay dato del día (HU-076 E1 fallback).
 *
 * <p>Multi-tenant: cada empresa lleva su propia TRM (consistente con el modelo de filtro
 * por company_id del resto del sistema).
 */
@Entity
@Table(name = "trm_historica")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrmHistorica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Fecha de vigencia de la TRM. */
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    /** Código ISO de la moneda extranjera (USD, EUR, ...). */
    @Column(name = "currency_iso", nullable = false, length = 10)
    private String currencyIso;

    /** Valor en COP por una unidad de la moneda extranjera. */
    @Column(name = "valor_cop", nullable = false, precision = 18, scale = 6)
    private BigDecimal valorCop;

    /** MANUAL | OFICIAL | ULTIMA_PUBLICADA */
    @Column(name = "fuente", nullable = false, length = 20)
    @Builder.Default
    private String fuente = "MANUAL";

    @Column(name = "created_by")
    private Long createdBy;

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
        if (fuente == null) fuente = "MANUAL";
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

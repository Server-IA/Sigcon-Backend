package com.sigcon.backend.banks.bnk_cash_flow.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.banks.bnk_cash_flow.domain.model.enums.ProjectionPeriodicity;
import com.sigcon.backend.banks.bnk_cash_flow.domain.model.enums.ProjectionStatus;
import com.sigcon.backend.banks.bnk_cash_flow.domain.model.enums.ProjectionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BNK-RF-29 / BNK-RF-30 / BNK-RF-31 / BNK-RF-32
 *
 * Entidad principal del módulo de Flujo de Caja.
 * Gestiona las proyecciones de cash flow sin dependencia de empresa.
 *
 * Eliminación lógica: deletedAt + status = INACTIVA.
 * No se realiza eliminación física en base de datos.
 *
 * // TODO: integrar con módulo de auditoría en el futuro
 */
@Entity
@Table(name = "bnk_cash_flow_projections")
@SQLDelete(sql = "UPDATE bnk_cash_flow_projections SET deleted_at = NOW(), status = 'INACTIVA' WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CashFlowProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /**
     * Nombre único de la proyección. BNK-RF-29: validado como único en el sistema.
     */
    @Column(name = "name", nullable = false, length = 255)
    @NotBlank(message = "El nombre de la proyección no puede estar vacío")
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Fecha de inicio del período proyectado.
     * BNK-RF-29: startDate debe ser anterior a endDate.
     */
    @Column(name = "start_date", nullable = false)
    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate startDate;

    /**
     * Fecha de fin del período proyectado.
     * BNK-RF-29: endDate debe ser posterior a startDate.
     */
    @Column(name = "end_date", nullable = false)
    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", nullable = false, length = 30)
    @NotNull(message = "La periodicidad es obligatoria")
    private ProjectionPeriodicity periodicity;

    @Enumerated(EnumType.STRING)
    @Column(name = "projection_type", nullable = false, length = 20)
    @NotNull(message = "El tipo de proyección es obligatorio")
    private ProjectionType projectionType;

    /**
     * Saldo inicial del período. Debe ser >= 0.
     */
    @Column(name = "initial_balance", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "El saldo inicial es obligatorio")
    private BigDecimal initialBalance;

    /**
     * Flujo neto del período (puede ser negativo en proyecciones de egresos).
     */
    @Column(name = "net_flow", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "El flujo neto es obligatorio")
    private BigDecimal netFlow;

    /**
     * Saldo final calculado por el sistema: finalBalance = initialBalance + netFlow.
     * BNK-RF-29: no se acepta del cliente, se calcula en el servicio.
     */
    @Column(name = "final_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalBalance;

    /**
     * Moneda ISO 4217 (ej. "COP", "USD", "EUR").
     * Vincula la proyección con la moneda de las cuentas contables/bancarias.
     */
    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank(message = "La moneda (ISO 4217) es obligatoria")
    private String currency;

    /**
     * BNK-RF-31 — Estado del ciclo de vida de la proyección.
     * Valor inicial: BORRADOR.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectionStatus status;

    /**
     * BNK-RF-30 — Motivo de modificación requerido cuando status = APROBADA.
     */
    @Column(name = "modification_reason", length = 500)
    private String modificationReason;

    // ─────────────── Auditoría básica ───────────────

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * BNK-RF-31 — Soporte de eliminación lógica.
     * Cuando deletedAt != null el registro se considera eliminado.
     * @SQLDelete y @Where garantizan que las consultas JPA los excluyan automáticamente.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ─────────────── Lifecycle callbacks ───────────────

    @PrePersist
    protected void onCreate() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = ProjectionStatus.BORRADOR;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

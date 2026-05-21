package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BNK-HU-061 / BNK-HU-073: partida conciliatoria — un movimiento del extracto
 * que el banco cargó/abonó pero NO está registrado en libros (GMF, comisión,
 * intereses, notas débito/crédito) y requiere un asiento de ajuste.
 *
 * Ciclo: PENDIENTE (marcada durante el pre-procesamiento, HU-061 E1) ->
 * RESUELTA_AJUSTE (cuando se genera el comprobante de ajuste, HU-073 E8) o
 * DESCARTADA. Multi-tenant.
 */
@Entity
@Table(name = "partidas_conciliatorias")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartidaConciliatoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Column(name = "financial_movement_id", nullable = false)
    private Long financialMovementId;

    /** GMF_NO_REGISTRADO | COMISION_NO_REGISTRADA | INTERES_GANADO_NO_REGISTRADO
     *  | INTERES_PAGADO_NO_REGISTRADO | NOTA_DEBITO_NO_REGISTRADA | NOTA_CREDITO_NO_REGISTRADA | OTRO */
    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    /** PENDIENTE | RESUELTA_AJUSTE | RESUELTA_PROXIMO_PERIODO | DESCARTADA */
    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "monto", nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    /** Código PUC sugerido para el débito (HU-073 E1). */
    @Column(name = "cuenta_debito_sugerida", length = 20)
    private String cuentaDebitoSugerida;

    /** Código PUC sugerido para el crédito (HU-073 E1). */
    @Column(name = "cuenta_credito_sugerida", length = 20)
    private String cuentaCreditoSugerida;

    /** journal_entries.id del comprobante de ajuste que la resolvió (HU-073 E8). */
    @Column(name = "comprobante_ajuste_id")
    private Long comprobanteAjusteId;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    // BNK-HU-074: antigüedad y alertas de partidas pendientes.
    /** Fecha de origen de la partida (fecha del movimiento del extracto). */
    @Column(name = "fecha_origen")
    private java.time.LocalDate fechaOrigen;

    /** Días de antigüedad = hoy - fecha_origen (recalculado por job diario, HU-074 E1). */
    @Column(name = "dias_antiguedad")
    private Integer diasAntiguedad;

    /** HU-074 E3: marca idempotente de la alerta a 60 días. */
    @Column(name = "alerta_60d_at")
    private LocalDateTime alerta60dAt;

    /** HU-074 E4: marca idempotente de la alerta a 90 días. */
    @Column(name = "alerta_90d_at")
    private LocalDateTime alerta90dAt;

    /** HU-074 E8: justificación cuando se resuelve como RESUELTA_PROXIMO_PERIODO. */
    @Column(name = "motivo_resolucion", length = 500)
    private String motivoResolucion;

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
        if (estado == null) estado = "PENDIENTE";
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

package com.sigcon.backend.third_parties.commercial_data.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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
 * Registro inmutable de cambios realizados sobre datos comerciales.
 * No se aplica soft delete ya que es auditoria permanente.
 */
@Entity
@Table(name = "commercial_data_history")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommercialDataHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** ID del registro de datos comerciales al que pertenece el cambio */
    @Column(name = "commercial_data_id", nullable = false)
    private Long commercialDataId;

    /** Nombre del campo que cambio */
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    /** Valor anterior del campo (texto) */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** Nuevo valor del campo (texto) */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** ID del usuario que realizo el cambio */
    @Column(name = "changed_by")
    private Long changedBy;

    /**
     * PT-03 (TER-RF-11/12, 2026-06-02): motivo del cambio (o justificacion de
     * eliminacion) ingresado por el usuario. Se persiste en cada registro del
     * historial para trazabilidad funcional.
     */
    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    /** Fecha y hora del cambio */
    @Column(name = "changed_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime changedAt;

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

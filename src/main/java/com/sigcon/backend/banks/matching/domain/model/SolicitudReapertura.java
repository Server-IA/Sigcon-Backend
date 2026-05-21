package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BNK-HU-075: solicitud de reapertura de una sesión CERRADA. Requiere motivo
 * (>=100 chars) + evidencia. La aprueba un REVISOR_FISCAL distinto del solicitante
 * (segregación, HU-067). Multi-tenant.
 */
@Entity
@Table(name = "solicitudes_reapertura")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudReapertura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "sesion_id", nullable = false)
    private Long sesionId;

    @Column(name = "solicitante_id", nullable = false)
    private Long solicitanteId;

    @Column(name = "motivo", length = 2000, nullable = false)
    private String motivo;

    @Column(name = "tipo_cambio_esperado", length = 200)
    private String tipoCambioEsperado;

    @Column(name = "evidencia_file_name", length = 300)
    private String evidenciaFileName;

    @Column(name = "evidencia_hash", length = 64)
    private String evidenciaHash;

    /** PENDIENTE | APROBADA | RECHAZADA */
    @Column(name = "estado", nullable = false, length = 16)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "aprobador_id")
    private Long aprobadorId;

    @Column(name = "aprobada_at")
    private LocalDateTime aprobadaAt;

    @Column(name = "motivo_rechazo", length = 1000)
    private String motivoRechazo;

    /** sesiones_conciliacion.id de la nueva versión creada al aprobar (HU-075 E5). */
    @Column(name = "nueva_sesion_id")
    private Long nuevaSesionId;

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

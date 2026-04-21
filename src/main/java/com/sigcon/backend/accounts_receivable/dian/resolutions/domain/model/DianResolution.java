package com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resolucion DIAN que autoriza la numeracion de facturas electronicas.
 * Incluye rango autorizado, vigencia y clave tecnica para el calculo del CUFE
 * segun la Resolucion 0042 de 2020 y el Anexo Tecnico de facturacion electronica.
 */
@Entity
@Table(name = "dian_resolutions")
@SQLDelete(sql = "UPDATE dian_resolutions SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DianResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Numero oficial de la resolucion autorizada por la DIAN. */
    @Column(name = "resolution_number", nullable = false, length = 100)
    private String resolutionNumber;

    /** Prefijo autorizado para la numeracion (por ejemplo "FV", "FE"). */
    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix;

    /** Primer numero autorizado del rango. */
    @Column(name = "start_number", nullable = false)
    private Long startNumber;

    /** Ultimo numero autorizado del rango. */
    @Column(name = "end_number", nullable = false)
    private Long endNumber;

    /** Numero actual de la numeracion (se incrementa al asignar consecutivo). */
    @Column(name = "current_number", nullable = false)
    @Builder.Default
    private Long currentNumber = 0L;

    /** Fecha de inicio de vigencia de la resolucion. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Fecha de finalizacion de vigencia de la resolucion. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Clave tecnica entregada por la DIAN para calcular el CUFE. */
    @Column(name = "technical_key", length = 200)
    private String technicalKey;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DianResolutionStatus status = DianResolutionStatus.ACTIVE;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    public void prePersist() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = DianResolutionStatus.ACTIVE;
        if (this.currentNumber == null) this.currentNumber = this.startNumber != null ? this.startNumber - 1 : 0L;
    }

    @PreUpdate
    public void preUpdate() {
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

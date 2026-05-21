package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BNK-HU-066/067/075/077: sesión de conciliación con firma electrónica, segregación
 * de funciones y versionado (reapertura). Es una entidad NUEVA y separada de la
 * legacy {@code bank_reconciliation_sessions} para no romper el flujo simple ya
 * validado (R2: aditivo).
 *
 * Máquina de estados: BORRADOR -> EN_REVISION -> APROBADA -> CERRADA, con REABIERTA
 * como nueva versión (HU-075). Multi-tenant.
 */
@Entity
@Table(name = "sesiones_conciliacion")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SesionConciliacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    /** BORRADOR | EN_REVISION | APROBADA | CERRADA | REABIERTA */
    @Column(name = "estado", nullable = false, length = 16)
    @Builder.Default
    private String estado = "BORRADOR";

    /** HU-075 E5: versión (1 = original; reapertura incrementa). */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    /** HU-075 E5: sesión origen de la que se reabrió (null si es la original). */
    @Column(name = "sesion_origen_id")
    private Long sesionOrigenId;

    @Column(name = "saldo_extracto", precision = 20, scale = 2)
    private BigDecimal saldoExtracto;

    @Column(name = "saldo_libros", precision = 20, scale = 2)
    private BigDecimal saldoLibros;

    @Column(name = "diferencia", precision = 20, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "enviada_revision_by")
    private Long enviadaRevisionBy;

    @Column(name = "enviada_revision_at")
    private LocalDateTime enviadaRevisionAt;

    @Column(name = "aprobada_by")
    private Long aprobadaBy;

    @Column(name = "aprobada_at")
    private LocalDateTime aprobadaAt;

    @Column(name = "cerrada_by")
    private Long cerradaBy;

    @Column(name = "cerrada_at")
    private LocalDateTime cerradaAt;

    /** HU-066: firmas_electronicas.id del elaborador y del revisor. */
    @Column(name = "firma_elaborador_id")
    private Long firmaElaboradorId;

    @Column(name = "firma_revisor_id")
    private Long firmaRevisorId;

    /** HU-077 E4 / HU-062: hash SHA-256 del extracto original conciliado. */
    @Column(name = "hash_extracto", length = 64)
    private String hashExtracto;

    /** archivos_soporte.id del informe PDF firmado (HU-077). */
    @Column(name = "informe_archivo_id")
    private Long informeArchivoId;

    /** HU-067 E5: modo flexible (relaja segregación con doble firma + motivo). */
    @Column(name = "modo_flexible", nullable = false)
    @Builder.Default
    private Boolean modoFlexible = false;

    @Column(name = "motivo_excepcion", length = 1000)
    private String motivoExcepcion;

    @Column(name = "notas", length = 1000)
    private String notas;

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
        if (estado == null) estado = "BORRADOR";
        if (version == null) version = 1;
        if (modoFlexible == null) modoFlexible = false;
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

package com.sigcon.backend.banks.dian.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BNK-HU-079 E6: histórico de generaciones de información exógena (formatos 1647/1010/1011).
 * Append-only para trazabilidad ante DIAN si hubo presentaciones múltiples del mismo año.
 */
@Entity
@Table(name = "exogena_generaciones")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExogenaGeneracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "ano_fiscal", nullable = false)
    private Integer anoFiscal;

    /** 1647 | 1010 | 1011 */
    @Column(name = "formato", nullable = false, length = 10)
    private String formato;

    /** csv | xml */
    @Column(name = "formato_archivo", nullable = false, length = 10)
    @Builder.Default
    private String formatoArchivo = "csv";

    @Column(name = "hash_archivo", length = 64)
    private String hashArchivo;

    @Column(name = "archivo_soporte_id")
    private Long archivoSoporteId;

    @Column(name = "generado_by")
    private Long generadoBy;

    @Column(name = "generado_at", nullable = false)
    private LocalDateTime generadoAt;

    @PrePersist
    void prePersist() {
        if (companyId == null) companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (generadoAt == null) generadoAt = LocalDateTime.now();
        if (formatoArchivo == null) formatoArchivo = "csv";
    }

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

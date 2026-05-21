package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BNK-HU-066 E1 / HU-067 E5: configuración de firma de conciliación por empresa.
 * Define métodos permitidos (OTP/CERTIFICADO/BIOMETRICA), si el revisor fiscal
 * exige certificado, y si la empresa opera en modo flexible (relaja segregación).
 * Multi-tenant (una fila por empresa).
 */
@Entity
@Table(name = "bnk_config_firma")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigFirmaConciliacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** CSV de métodos permitidos: OTP,CERTIFICADO,BIOMETRICA */
    @Column(name = "metodos_permitidos", length = 100, nullable = false)
    @Builder.Default
    private String metodosPermitidos = "OTP";

    /** HU-066 E1: si el REVISOR_FISCAL exige certificado digital obligatorio. */
    @Column(name = "exige_cert_revisor", nullable = false)
    @Builder.Default
    private Boolean exigeCertRevisor = false;

    /** HU-067 E5: modo flexible (false = ESTRICTO por defecto). */
    @Column(name = "modo_flexible", nullable = false)
    @Builder.Default
    private Boolean modoFlexible = false;

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
        if (metodosPermitidos == null) metodosPermitidos = "OTP";
        if (exigeCertRevisor == null) exigeCertRevisor = false;
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

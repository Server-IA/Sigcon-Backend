package com.sigcon.backend.banks.matching.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BNK-HU-066: firma electrónica de una conciliación. Append-only (sin deleted_at):
 * los datos de la firma son INMUTABLES tras la inserción (E8). Multi-tenant.
 */
@Entity
@Table(name = "firmas_electronicas")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmaElectronica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "sesion_id", nullable = false)
    private Long sesionId;

    /** ELABORADOR | REVISOR */
    @Column(name = "rol_firma", nullable = false, length = 20)
    private String rolFirma;

    @Column(name = "firmante_user_id", nullable = false)
    private Long firmanteUserId;

    @Column(name = "firmante_nombre", length = 200)
    private String firmanteNombre;

    @Column(name = "firmante_documento", length = 40)
    private String firmanteDocumento;

    /** Tarjeta profesional (T.P.) — obligatoria para CONTADOR/REVISOR_FISCAL (E7). */
    @Column(name = "firmante_tp", length = 40)
    private String firmanteTp;

    @Column(name = "firmante_rol", length = 40)
    private String firmanteRol;

    /** OTP | CERTIFICADO | BIOMETRICA */
    @Column(name = "metodo_firma", nullable = false, length = 20)
    private String metodoFirma;

    /** HU-066 E4: JSON canónico del documento firmado. */
    @Column(name = "payload_firma", columnDefinition = "TEXT")
    private String payloadFirma;

    /** HU-066 E4: SHA-256 del payload canónico. */
    @Column(name = "hash_documento", length = 64)
    private String hashDocumento;

    /** Base64 del resultado de la firma (stand-in en dev). */
    @Column(name = "firma_resultado", columnDefinition = "TEXT")
    private String firmaResultado;

    /** HU-066 E5: sello de tiempo (TSA si hay; si no, reloj del servidor). */
    @Column(name = "sello_tiempo")
    private LocalDateTime selloTiempo;

    @Column(name = "ip_firmante", length = 60)
    private String ipFirmante;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (companyId == null) companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        createdAt = LocalDateTime.now();
        if (selloTiempo == null) selloTiempo = createdAt;
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

package com.sigcon.backend.banks.trm.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BNK-HU-076 E8: política de TRM por empresa.
 *
 * <p>{@code politicaTrm} define qué tasa se considera la "aplicada" a cada movimiento
 * en moneda extranjera, según la política contable adoptada (NIC 21):
 * <ul>
 *   <li>FECHA_MOVIMIENTO (default): la TRM del día del movimiento. Es lo estándar para
 *       reconocimiento inicial; la diferencia en cambio surge al revaluar al cierre.</li>
 *   <li>FECHA_CIERRE: valora todo a la TRM de cierre (sin diferencia en cambio diaria).</li>
 * </ul>
 */
@Entity
@Table(name = "bnk_config_trm")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigTrm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    /** FECHA_MOVIMIENTO | FECHA_CIERRE */
    @Column(name = "politica_trm", nullable = false, length = 20)
    @Builder.Default
    private String politicaTrm = "FECHA_MOVIMIENTO";

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
        if (politicaTrm == null) politicaTrm = "FECHA_MOVIMIENTO";
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}

package com.sigcon.backend.platform.companies.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Empresa cliente de la plataforma SIGCON (tenant).
 *
 * <p>Raiz del modelo multi-tenant. Toda entidad tenant-scoped (invoices,
 * journal_entries, third_parties, etc.) tiene FK a {@code companies(id)}.
 *
 * <p>Ciclo de vida:
 * <ol>
 *   <li>{@code PLATFORM_ADMIN} crea la empresa + el admin inicial en un flow
 *       atomico (HU-PA-RF-64). Al crearla, el sistema auto-crea:
 *       <ul>
 *         <li>18 cuentas operativas default + 18 mapeos PUC (HU-TENANT-04).</li>
 *         <li>12 periodos contables OPEN del ano actual (HU-TENANT-05).</li>
 *       </ul>
 *   </li>
 *   <li>El admin de la empresa opera normalmente con scope tenant.</li>
 *   <li>Soft-delete: al eliminar una empresa se bloquean logins de sus
 *       usuarios (HU-PA-RF-01 E-MT-03) pero sus datos siguen en BD por
 *       cumplimiento tributario (≥5 anios de retencion, Estatuto Tributario
 *       Art. 632).</li>
 * </ol>
 *
 * <p>El {@code @Where} NO se aplica aqui (Company es la entidad maestra
 * del tenant; su listado lo hace {@code PLATFORM_ADMIN}).
 *
 * Ver CLAUDE.md seccion "Decision estrategica 2026-04-19: VUELTA A MULTI-TENANT".
 */
@Entity
@Table(name = "companies")
@SQLDelete(sql = "UPDATE companies SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NIT sin digito de verificacion. Unique por empresa activa (no global). */
    @NotBlank(message = "El NIT es obligatorio")
    @Column(name = "nit", nullable = false, length = 20)
    private String nit;

    /** Digito de verificacion (0-9). */
    @Column(name = "dv", length = 2)
    private String dv;

    /** Razon social de la empresa. */
    @NotBlank(message = "La razon social es obligatoria")
    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "legal_representative", length = 200)
    private String legalRepresentative;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "address", length = 300)
    private String address;

    /** Etiqueta libre: "10 empleados", "50 empleados", "PYME", etc. */
    @Column(name = "company_size", length = 50)
    private String companySize;

    /** FK a catalogo type_organization (Sociedad, Natural, etc.). */
    @Column(name = "type_organization_id")
    private Long typeOrganizationId;

    /** FK a catalogo type_regimen (Comun, Simplificado, etc.). */
    @Column(name = "type_regimen_id")
    private Long typeRegimenId;

    /** ACTIVE | INACTIVE. Solo empresas ACTIVE pueden tener login de usuarios. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime deletedAt;

    /** Sprint 2 BRAND-01: configuracion de identidad visual en JSON. */
    @Column(name = "brand_config", columnDefinition = "TEXT")
    private String brandConfig;

    /** Sprint 2 NAV-01: orden personalizado de modulos en JSON. */
    @Column(name = "module_order", columnDefinition = "TEXT")
    private String moduleOrder;

    public enum CompanyStatus {
        ACTIVE,
        INACTIVE
    }
}

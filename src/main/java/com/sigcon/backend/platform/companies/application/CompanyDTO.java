package com.sigcon.backend.platform.companies.application;

import java.time.LocalDateTime;

import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.model.Company.CompanyStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO de respuesta para lectura de empresas. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Empresa (tenant) de la plataforma SIGCON")
public class CompanyDTO {

    private Long id;
    private String nit;
    private String dv;
    private String businessName;
    private String legalRepresentative;
    private String email;
    private String phone;
    private String address;
    private String companySize;
    private Long typeOrganizationId;
    private Long typeRegimenId;
    private CompanyStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * QA Bloque PA Bug 56 (HU-PA-PLAT-01 E1, 2026-05-09): id del usuario admin
     * creado en el flujo with-admin. Solo se popula en {@link CompanyService#createWithAdmin}.
     * Para listados/get-by-id se queda null.
     */
    @Schema(description = "Id del primer admin creado (solo en respuesta de /with-admin)", example = "203")
    private Long adminUserId;
    @Schema(description = "Email del primer admin creado (solo en respuesta de /with-admin)")
    private String adminEmail;
    @Schema(description = "Username del primer admin creado (solo en respuesta de /with-admin)")
    private String adminUsername;

    /**
     * QA Bloque PA Bug 57 (HU-PA-PLAT-02 E1+E4, 2026-05-09): metricas opcionales para
     * la vista listado/detalle de empresas. Pueden venir null si no se calcularon.
     */
    @Schema(description = "Numero de usuarios activos (no PLATFORM_ADMIN) en la empresa")
    private Long activeUsersCount;
    @Schema(description = "Numero de periodos contables OPEN en el anio actual")
    private Long openPeriodsCount;
    @Schema(description = "Estado AAEF: OK si no hay lotes ACK_FAILED, ERROR si los hay")
    private String aaefStatus;
    @Schema(description = "Numero de lotes AAEF totales en la empresa")
    private Long aaefBatchesCount;
    @Schema(description = "Fecha del ultimo login de cualquier usuario de la empresa")
    private LocalDateTime lastLoginAt;
    @Schema(description = "Conteo de usuarios por nombre de rol (solo en detalle GET /{id})")
    private java.util.Map<String, Long> usersByRole;
    @Schema(description = "Conteo de periodos por estado (OPEN/CLOSED/LOCKED)")
    private java.util.Map<String, Long> periodsByStatus;
    @Schema(description = "Id del ultimo lote AAEF recibido")
    private Long aaefLastBatchId;
    @Schema(description = "Fecha del ultimo lote AAEF recibido")
    private LocalDateTime aaefLastBatchAt;

    public static CompanyDTO from(Company c) {
        return CompanyDTO.builder()
                .id(c.getId())
                .nit(c.getNit())
                .dv(c.getDv())
                .businessName(c.getBusinessName())
                .legalRepresentative(c.getLegalRepresentative())
                .email(c.getEmail())
                .phone(c.getPhone())
                .address(c.getAddress())
                .companySize(c.getCompanySize())
                .typeOrganizationId(c.getTypeOrganizationId())
                .typeRegimenId(c.getTypeRegimenId())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}

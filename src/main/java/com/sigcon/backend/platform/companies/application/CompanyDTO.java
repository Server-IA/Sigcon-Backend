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

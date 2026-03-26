package com.sigcon.backend.parametrization.companies.application;

import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDTO {

    private Long id;
    private String name;
    private String nit;
    private String dv;
    private String legalRepresentative;
    private String email;
    private String size;
    private String phone;
    private String logo;
    private CompanyStatus status;

    private Long typeRegimeId;
    private String typeRegimeName;
    private String typeRegimeCode;
    private TypeRegimenDTO typeRegimen;

    private Long typeOrganizationId;
    private String typeOrganizationName;
    private String typeOrganizationCode;
    private TypeOrganizationDTO typeOrganization;

    private Long mainLocationId;
    private String mainAddress;

    // Todas las sedes asociadas
    private List<CompanyLocationDTO> locations;

    // Retenciones asociadas
    private List<WithholdingDTO> withholdings;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}


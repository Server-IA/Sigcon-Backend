package com.sigcon.backend.third_parties.third_parties.application;

import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyDTO {
    private Long id;
    private String thirdPartyCode;
    private String nit;
    private String dv;
    private String businessName;
    private List<ThirdPartyRoleCatalogDTO> roles;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<Long> roleIds;
    private ThirdPartyStatusCatalogDTO status;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long statusId;
    private String blockingReason;
    private MunicipalityDTO municipality;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long municipalityId;
    private TypeOrganizationDTO typeOrganization;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long typeOrganizationId;
    private TypeRegimenDTO typeRegimen;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Long typeRegimenId;
    private List<WithholdingDTO> withholdings;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<Long> withholdingIds;
    private BigDecimal creditLimit;
    private String paymentTerms;
    private String marketSegment;
    private List<ThirdContactDTO> contacts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


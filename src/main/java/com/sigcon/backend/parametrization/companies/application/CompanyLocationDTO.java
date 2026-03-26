package com.sigcon.backend.parametrization.companies.application;

import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyLocationDTO {

    private Long id;
    private String name;
    private String description;
    private String address;
    private CompanyStatus status;
    private Boolean isMain;

    private Long municipalityId;
    private MunicipalityDTO municipality;
    private CountryDTO country;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}


package com.sigcon.backend.parametrization.companies.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.sigcon.backend.parametrization.companies.domain.model.CompanyStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyLocationRequest {

    @NotBlank(message = "El nombre de la sede es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
    private String name;

    @Size(max = 500, message = "La descripcion no puede superar 500 caracteres")
    private String description;

    @NotBlank(message = "La direccion es obligatoria")
    @Size(max = 255, message = "La direccion no puede superar 255 caracteres")
    private String address;

    private CompanyStatus status;

    private Boolean isMain;

    @NotNull(message = "El municipio es obligatorio")
    private Long municipalityId;
}


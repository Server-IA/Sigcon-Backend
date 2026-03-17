package com.sigcon.backend.parametrization.companies.application;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyLocationRequest {

    @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
    private String name;

    @Size(max = 500, message = "La descripcion no puede superar 500 caracteres")
    private String description;

    @Size(max = 255, message = "La direccion no puede superar 255 caracteres")
    private String address;

    private String status;

    private Boolean isMain;

    private Long municipalityId;
}


package com.sigcon.backend.parametrization.companies.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCompanyRequest {

    @NotBlank(message = "El nombre de la compañía es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
    private String name;

    @NotBlank(message = "El NIT es obligatorio")
    @Size(max = 15, message = "El NIT no puede superar 15 caracteres")
    private String nit;

    @NotBlank(message = "El DV es obligatorio")
    @Size(max = 1, message = "El DV debe tener 1 caracter")
    private String dv;

    @Size(max = 255, message = "El representante legal no puede superar 255 caracteres")
    private String legalRepresentative;

    @Email(message = "El correo electronico no es valido")
    @Size(max = 255, message = "El correo no puede superar 255 caracteres")
    private String email;

    @Size(max = 45, message = "El tamano no puede superar 45 caracteres")
    private String size;

    @Size(max = 12, message = "El telefono no puede superar 12 caracteres")
    private String phone;

    @Size(max = 255, message = "La URL del logo no puede superar 255 caracteres")
    private String logo;

    private String status;

    @NotNull(message = "El tipo de regimen es obligatorio")
    private Long typeRegimeId;

    @NotNull(message = "El tipo de organizacion es obligatorio")
    private Long typeOrganizationId;

    @NotNull(message = "Debe proporcionar al menos una sede para la compañía")
    @jakarta.validation.Valid
    private List<CreateCompanyLocationRequest> locations;
}


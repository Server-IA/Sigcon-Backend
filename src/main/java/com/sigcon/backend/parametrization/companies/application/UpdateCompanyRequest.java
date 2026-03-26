package com.sigcon.backend.parametrization.companies.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.sigcon.backend.parametrization.companies.domain.model.CompanyStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyRequest {

    @NotBlank(message = "El nombre es requerido")
    @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
    private String name;

    @NotBlank(message = "El NIT es requerido")
    @Size(max = 15, message = "El NIT no puede superar 15 caracteres")
    private String nit;

    @NotBlank(message = "El DV es requerido")
    @Size(max = 1, message = "El DV debe tener 1 caracter")
    private String dv;

    @NotBlank(message = "El representante legal es requerido")
    @Size(max = 255, message = "El representante legal no puede superar 255 caracteres")
    private String legalRepresentative;

    @Email(message = "El correo electronico no es valido")
    @Size(max = 255, message = "El correo no puede superar 255 caracteres")
    private String email;

    @Size(max = 45, message = "El tamano no puede superar 45 caracteres")
    private String size;

    @NotBlank(message = "El telefono es requerido")
    @Size(max = 12, message = "El telefono no puede superar 12 caracteres")
    private String phone;

    private LogoCompany logo;

    private CompanyStatus status;

    private Long typeRegimeId;

    private Long typeOrganizationId;

    private List<Long> withholdings;
}


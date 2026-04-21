package com.sigcon.backend.platform.companies.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request para crear una empresa nueva (HU-PA-RF-60). */
@Data
@Schema(description = "Alta de empresa (PLATFORM_ADMIN)")
public class CreateCompanyRequest {

    @NotBlank(message = "El NIT es obligatorio")
    @Size(max = 20, message = "NIT maximo 20 caracteres")
    @Pattern(regexp = "^[0-9]+$", message = "NIT debe ser numerico")
    @Schema(example = "9001234567", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nit;

    @Pattern(regexp = "^[0-9]?$", message = "DV debe ser un digito 0-9 o vacio")
    @Schema(example = "1")
    private String dv;

    @NotBlank(message = "La razon social es obligatoria")
    @Size(max = 200)
    @Schema(example = "ACME Agroindustria SAS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String businessName;

    @Size(max = 200)
    @Schema(example = "Juan Perez")
    private String legalRepresentative;

    @Size(max = 100)
    @Schema(example = "contacto@acme.co")
    private String email;

    @Size(max = 30)
    @Schema(example = "6011234567")
    private String phone;

    @Size(max = 300)
    private String address;

    @Size(max = 50)
    @Schema(example = "50 empleados")
    private String companySize;

    private Long typeOrganizationId;
    private Long typeRegimenId;
}

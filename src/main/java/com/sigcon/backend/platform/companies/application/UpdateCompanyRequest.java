package com.sigcon.backend.platform.companies.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request para actualizar campos de una empresa (HU-PA-RF-61).
 * Todos los campos opcionales; solo se actualizan los que vienen con valor.
 * El NIT se permite cambiar si la empresa no tiene movimientos contables
 * (validacion en service).
 */
@Data
@Schema(description = "Edicion de empresa (PLATFORM_ADMIN)")
public class UpdateCompanyRequest {

    @Size(max = 20)
    @Pattern(regexp = "^[0-9]*$", message = "NIT debe ser numerico")
    private String nit;

    @Pattern(regexp = "^[0-9]?$", message = "DV debe ser un digito")
    private String dv;

    @Size(max = 200, message = "La razon social no puede superar 200 caracteres")
    // Pendientes PA 2026-05-30: letras, numeros, espacios y los simbolos & . ( ) /.
    @Pattern(regexp = "(?U)^[\\p{L}\\p{N} &.()/]+$",
            message = "La razon social solo admite letras, numeros, espacios y los simbolos & . ( ) /")
    private String businessName;

    @Size(max = 200)
    private String legalRepresentative;

    @Size(max = 100)
    private String email;

    @Size(max = 30)
    private String phone;

    @Size(max = 300)
    private String address;

    @Size(max = 50)
    private String companySize;

    private Long typeOrganizationId;
    private Long typeRegimenId;
}

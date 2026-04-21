package com.sigcon.backend.platform.companies.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * HU-PLAT-02: Alta atomica de empresa + primer usuario ADMIN de esa empresa.
 * El PLATFORM_ADMIN usa este endpoint para onboarding de clientes nuevos.
 */
@Data
@Schema(description = "Alta empresa + primer admin (transaccion atomica)")
public class CreateCompanyWithAdminRequest {

    @Valid
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private CreateCompanyRequest company;

    @NotBlank(message = "El nombre del admin es obligatorio")
    @Size(max = 100)
    @Schema(example = "Ana", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adminFirstName;

    @NotBlank(message = "El apellido del admin es obligatorio")
    @Size(max = 100)
    @Schema(example = "Martinez", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adminLastName;

    @NotBlank @Email(message = "Email del admin invalido")
    @Size(max = 150)
    @Schema(example = "ana.martinez@acme.co", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adminEmail;

    @NotBlank(message = "El username del admin es obligatorio")
    @Size(min = 3, max = 50)
    @Schema(example = "ana.martinez", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adminUsername;

    @NotBlank(message = "La password del admin es obligatoria")
    @Size(min = 6, max = 100, message = "Password entre 6 y 100 caracteres")
    @Schema(example = "Passw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adminPassword;
}

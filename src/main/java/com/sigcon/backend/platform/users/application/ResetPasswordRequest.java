package com.sigcon.backend.platform.users.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request para HU-PA-PLAT-04 E4: resetear contrasenia de usuario de cualquier
 * empresa. Solo invocable por PLATFORM_ADMIN.
 */
@Data
@Schema(description = "Reset de contrasenia de usuario por PLATFORM_ADMIN")
public class ResetPasswordRequest {

    @NotBlank(message = "La contrasenia temporal es obligatoria")
    @Size(min = 6, message = "La contrasenia debe tener al menos 6 caracteres")
    @Schema(description = "Contrasenia temporal asignada; el usuario debera cambiarla",
            example = "TempPass123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}

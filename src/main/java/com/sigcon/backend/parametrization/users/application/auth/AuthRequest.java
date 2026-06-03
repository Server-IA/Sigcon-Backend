package com.sigcon.backend.parametrization.users.application.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {

    private String name;
    private String lastname;
    private String email;
    private String username;
    @Schema(description = "Contraseña del usuario", example = "123456")
    private String password;
    private String avatar;

    @Schema(description = "Username o email del usuario", example = "superadmin@gmail.com")
    private String usernameOrEmail;

    // PA-RF-01 v3.0 (Control de Cambios PA): metadata opcional de dispositivo.
    // La IP no viaja en el body; el controller la extrae del request (X-Forwarded-For).
    @Schema(description = "Identificador opcional del dispositivo (PA-RF-01)", example = "web-chrome-001")
    private String deviceId;

    @Schema(description = "User-Agent del cliente (PA-RF-01); si se omite el controller usa el header", example = "Mozilla/5.0")
    private String userAgent;

}

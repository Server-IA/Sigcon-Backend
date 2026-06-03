package com.sigcon.backend.parametrization.users.application.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PA-RF-01 v3.0: payload para renovar el access token con un refresh token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenRequest {

    @Schema(description = "Refresh token entregado en el login", example = "a1b2c3...")
    private String refreshToken;

    @Schema(description = "Identificador opcional del dispositivo", example = "web-chrome-001")
    private String deviceId;
}

package com.sigcon.backend.integration.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * HU-INT-RF-11 (forensia): DTO devuelto al admin que consulta el log de
 * tokens JWT validados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Entrada del log forense de tokens JWT validados.")
public class JwtAuditLogDTO {

    @Schema(description = "ID del registro", example = "1024")
    private Long id;

    @Schema(description = "Key ID del JWK que firmo el token", example = "mock-idp-a1b2c3d4")
    private String kid;

    @Schema(description = "Subject del token (claim sub)", example = "agrofusion")
    private String subject;

    @Schema(description = "Claim iat (cuando se emitio el token)", example = "2026-04-16T09:00:00")
    private LocalDateTime issuedAt;

    @Schema(description = "Claim exp (cuando vence el token)", example = "2026-04-16T10:00:00")
    private LocalDateTime expiresAt;

    @Schema(description = "Issuer del token (claim iss)", example = "http://localhost:8080/mock-idp")
    private String issuer;

    @Schema(description = "Scope efectivo extraido del token", example = "aaef:lote:enviar")
    private String scope;

    @Schema(description = "Resultado de la validacion",
            example = "VALID",
            allowableValues = {"VALID", "EXPIRED", "INVALID_SCOPE", "WRONG_ISSUER",
                               "INVALID_SIGNATURE", "MALFORMED", "JWKS_UNAVAILABLE"})
    private String result;

    @Schema(description = "Detalle del error si fallo", example = "Scope insuficiente: se requiere aaef:lote:enviar")
    private String errorMessage;

    @Schema(description = "IP remota del request", example = "192.168.1.50")
    private String remoteIp;

    @Schema(description = "User-Agent del cliente", example = "AgroFusion-Client/1.0")
    private String userAgent;

    @Schema(description = "Path del endpoint", example = "/api/contabilidad/aaef")
    private String requestPath;

    @Schema(description = "Cuando se valido", example = "2026-04-16T09:15:32")
    private LocalDateTime validatedAt;
}

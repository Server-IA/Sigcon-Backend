package com.sigcon.backend.integration.apikeys.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PA-RF-28 (Pendientes PA): cuerpo para revocar una API Key. El motivo es
 * obligatorio (minimo 20 caracteres) para trazabilidad.
 */
@Data
@Schema(description = "Revocacion de API Key (motivo minimo 20 caracteres)")
public class RevokeApiKeyRequest {

    @NotBlank(message = "Debe indicar el motivo de la revocacion")
    @Size(min = 20, max = 200, message = "El motivo de la revocacion debe tener entre 20 y 200 caracteres")
    private String reason;
}

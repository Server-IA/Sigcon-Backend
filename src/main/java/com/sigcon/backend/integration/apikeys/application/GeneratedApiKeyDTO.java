package com.sigcon.backend.integration.apikeys.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * PA-RF-28 (Pendientes PA): respuesta de la GENERACION de una API Key.
 *
 * <p>Es la UNICA vez que se devuelve la clave en texto plano ({@code plainKey}).
 * El sistema solo persiste su hash SHA-256; si se pierde la clave debe revocarse
 * y emitirse una nueva.
 */
@Data
@Builder
@Schema(description = "Resultado de generar una API Key (la clave en claro se muestra una sola vez)")
public class GeneratedApiKeyDTO {

    @Schema(description = "Metadata de la credencial recien creada")
    private ApiKeyDTO key;

    @Schema(description = "Clave COMPLETA en texto plano. Solo se muestra aqui, una sola vez. "
            + "Guardela en un lugar seguro; el sistema solo almacena su hash.",
            example = "SIGCON-AAEF-a1b2c3d4-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
    private String plainKey;

    @Schema(description = "Aviso de seguridad para mostrar al usuario.")
    @Builder.Default
    private String warning = "Esta es la unica vez que se muestra la clave completa. "
            + "Guardela de forma segura; el sistema solo almacena su hash. "
            + "Si la pierde, revoquela y genere una nueva.";
}

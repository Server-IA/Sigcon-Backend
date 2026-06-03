package com.sigcon.backend.integration.apikeys.interfaces.controller;

import com.sigcon.backend.integration.apikeys.application.ApiKeyDTO;
import com.sigcon.backend.integration.apikeys.application.GeneratedApiKeyDTO;
import com.sigcon.backend.integration.apikeys.application.RevokeApiKeyRequest;
import com.sigcon.backend.integration.apikeys.domain.service.ApiKeyService;
import com.sigcon.backend.utils.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PA-RF-28 (Pendientes PA, 2026-06-03): administracion del ciclo de vida de las
 * API Keys AAEF. Solo PLATFORM_ADMIN (las claves son infraestructura de
 * integracion cross-empresa).
 *
 * <p>La generacion devuelve la clave en texto plano una sola vez; el sistema
 * solo almacena el hash SHA-256.
 */
@RestController
@RequestMapping("/api/admin/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
@Tag(name = "PA-RF-28 - API Keys AAEF", description = "Ciclo de vida de credenciales AAEF (PLATFORM_ADMIN)")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;
    private final UserUtil userUtil;

    @PostMapping
    @Operation(summary = "Generar API Key (PA-RF-28)",
            description = "Genera una nueva API Key para la empresa. Devuelve la clave en texto plano "
                    + "UNA sola vez; el sistema solo persiste su hash SHA-256. Maximo 2 activas por empresa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clave generada (incluye plainKey una sola vez)"),
            @ApiResponse(responseCode = "400", description = "Empresa inexistente/inactiva o limite de claves activas alcanzado"),
            @ApiResponse(responseCode = "403", description = "No es PLATFORM_ADMIN")
    })
    public ResponseEntity<GeneratedApiKeyDTO> generate(
            @Parameter(description = "ID de la empresa propietaria de la clave", required = true)
            @RequestParam Long companyId) {
        Long actingUserId = userUtil.getUser().getId();
        return ResponseEntity.ok(apiKeyService.generate(companyId, actingUserId));
    }

    @GetMapping
    @Operation(summary = "Listar API Keys de una empresa (PA-RF-28)",
            description = "Retorna la metadata de las API Keys (id, prefix, status, fechas, last_used_at). "
                    + "NUNCA expone el hash ni la clave en texto plano.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de metadata"),
            @ApiResponse(responseCode = "403", description = "No es PLATFORM_ADMIN")
    })
    public ResponseEntity<List<ApiKeyDTO>> list(
            @Parameter(description = "ID de la empresa", required = true)
            @RequestParam Long companyId) {
        return ResponseEntity.ok(apiKeyService.list(companyId));
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revocar API Key (PA-RF-28)",
            description = "Revoca una API Key ACTIVE. Requiere motivo (minimo 20 caracteres). "
                    + "Tras revocarla deja de ser valida para AAEF de inmediato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clave revocada"),
            @ApiResponse(responseCode = "400", description = "Motivo invalido o la clave no esta activa"),
            @ApiResponse(responseCode = "403", description = "No es PLATFORM_ADMIN")
    })
    public ResponseEntity<ApiKeyDTO> revoke(
            @Parameter(description = "ID de la API Key a revocar", required = true)
            @PathVariable Long id,
            @Valid @RequestBody RevokeApiKeyRequest request) {
        Long actingUserId = userUtil.getUser().getId();
        return ResponseEntity.ok(apiKeyService.revoke(id, request.getReason(), actingUserId));
    }
}

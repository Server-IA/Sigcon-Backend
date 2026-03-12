package com.sigcon.backend.assets.assets_depreciation.interfaces;

import com.sigcon.backend.assets.assets_depreciation.application.ViewAssetDepreciationDTO;
import com.sigcon.backend.assets.assets_depreciation.domain.services.AssetDepreciationHistoryService;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * ACT-RF-02 — Consulta del histórico de depreciaciones.
 *
 * <p>Todos los endpoints están restringidos a {@code ROLE_SUPERADMIN}.</p>
 *
 * <h2>Endpoints disponibles</h2>
 * <ul>
 *   <li>GET /history/{assetId} — histórico completo de un activo.</li>
 *   <li>GET /history?period=YYYY-MM — todos los históricos de un período.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assets/depreciation")
@RequiredArgsConstructor
@Tag(name = "Assets - Depreciación", description = "ACT-RF-02: Cálculo automático de depreciación de activos")
public class AssetDepreciationHistoryController {

    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private final AssetDepreciationHistoryService assetDepreciationHistoryService;

    // ─── Endpoints ────────────────────────────────────────────────────────────

    /**
     * Retorna el histórico de depreciaciones de un activo específico,
     * ordenado por fecha de cálculo descendente.
     */
    @GetMapping("/history/{assetId}")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Histórico de depreciaciones por activo",
            description = "Retorna todos los registros históricos de depreciación de un activo " +
                    "ordenados por fecha de cálculo descendente (más reciente primero)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Histórico retornado exitosamente",
                    content = @Content(schema = @Schema(implementation = ViewAssetDepreciationDTO.class))),
            @ApiResponse(responseCode = "400", description = "ID de activo inválido",
                    content = @Content(schema = @Schema(implementation = ErrorRespondJson.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado — se requiere token JWT válido",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere ROLE_SUPERADMIN",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ErrorRespondJson.class)))
    })
    public ResponseEntity<?> findByAssetId(
            @Parameter(description = "ID del activo a consultar", example = "1", required = true)
            @PathVariable Long assetId) {

        if (assetId == null || assetId <= 0) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("El ID del activo debe ser un número positivo.")));
        }

        try {
            List<ViewAssetDepreciationDTO> history = assetDepreciationHistoryService.findByAssetId(assetId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error al consultar el histórico del activo {}: ", assetId, e);
            return ResponseEntity.internalServerError().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Error interno al consultar el histórico.")));
        }
    }

    /**
     * Retorna todos los registros históricos de depreciación de un período contable.
     */
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Histórico de depreciaciones por período",
            description = "Retorna todos los registros históricos de depreciación correspondientes al " +
                    "período contable indicado en formato YYYY-MM."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Histórico del período retornado exitosamente",
                    content = @Content(schema = @Schema(implementation = ViewAssetDepreciationDTO.class))),
            @ApiResponse(responseCode = "400", description = "Formato de período inválido — se esperaba YYYY-MM",
                    content = @Content(schema = @Schema(implementation = ErrorRespondJson.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado — se requiere token JWT válido",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere ROLE_SUPERADMIN",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ErrorRespondJson.class)))
    })
    public ResponseEntity<?> findByPeriod(
            @Parameter(description = "Período contable en formato YYYY-MM", example = "2026-03", required = true)
            @RequestParam String period) {

        if (period == null || !PERIOD_PATTERN.matcher(period.trim()).matches()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Formato de período inválido. Use YYYY-MM (ej: 2026-03).")));
        }

        try {
            List<ViewAssetDepreciationDTO> history = assetDepreciationHistoryService.findByPeriod(period.trim());
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error al consultar el histórico del período {}: ", period, e);
            return ResponseEntity.internalServerError().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Error interno al consultar el histórico del período.")));
        }
    }
}

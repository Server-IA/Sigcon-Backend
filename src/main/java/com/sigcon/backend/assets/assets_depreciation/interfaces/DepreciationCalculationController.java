package com.sigcon.backend.assets.assets_depreciation.interfaces;

import com.sigcon.backend.assets.assets_depreciation.application.DepreciationCalculationResponseDTO;
import com.sigcon.backend.assets.assets_depreciation.domain.services.DepreciationCalculationService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * ACT-RF-02 — Cálculo Automático de Depreciación.
 * <p>
 * Endpoint restringido a {@code ROLE_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/assets/depreciation")
@RequiredArgsConstructor
@Tag(name = "4. Módulo de Activos - Depreciación", description = "Endpoints para el cálculo automático de depreciación de activos")
public class DepreciationCalculationController {

        private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

        private final DepreciationCalculationService depreciationCalculationService;

        /**
         * POST /api/v1/assets/depreciation/calculate?period=YYYY-MM
         * <p>
         * Ejecuta el cálculo automático de depreciación para todos los activos
         * elegibles
         * en el período contable indicado.
         */
        @PostMapping("/calculate")
        @PreAuthorize("hasAnyAuthority('PERM_CALCULATE_DEPRECIATION','TEMP_PERM_CALCULATE_DEPRECIATION','TEMP_CALCULATE_DEPRECIATION','PERM_ACT.ACTIVOS.EJECUTAR_DEPRECIACION','TEMP_PERM_ACT.ACTIVOS.EJECUTAR_DEPRECIACION','TEMP_ACT.ACTIVOS.EJECUTAR_DEPRECIACION','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
        @Operation(summary = "Calcular depreciación automática", description = "Ejecuta el cálculo automático de depreciación de activos (ACT-RF-02).\n\n"
                        +
                        "**El proceso realiza lo siguiente:**\n" +
                        "1. Verifica que el período contable esté abierto con el módulo de Contabilidad General (CG).\n"
                        +
                        "2. Consulta activos desde el módulo `assets` filtrando por: estado (ACTIVO o EN_USO), vida útil > 0 y método configurado.\n"
                        +
                        "3. Valida clasificaciones contables y cuentas de depreciación consultando el módulo `lists_accounting`.\n"
                        +
                        "4. Consulta información del proveedor consultando el módulo `third_parties`.\n" +
                        "5. Aplica el cálculo matemático según el método configurado.\n" +
                        "6. Actualiza el valor depreciado del activo en base de datos.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Depreciación calculada exitosamente", content = @Content(schema = @Schema(implementation = DepreciationCalculationResponseDTO.class))),
                        @ApiResponse(responseCode = "400", description = "Vida útil no definida | Método no reconocido o no permitido", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "403", description = "Acceso denegado (Requiere ROLE_ADMIN)", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "404", description = "Cuenta de depreciación faltante o inactiva", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "422", description = "Operación no permitida. Período contable cerrado", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> calculate(
                        @Parameter(description = "Período contable en formato YYYY-MM (ej: 2026-03)", example = "2026-03", required = true) @RequestParam String period) {
                // Validar formato del período (MEJORA 5)
                if (period == null || !PERIOD_PATTERN.matcher(period.trim()).matches()) {
                        throw new IllegalArgumentException("Formato de período inválido. Use YYYY-MM (ej: 2026-03)");
                }

                DepreciationCalculationResponseDTO response = depreciationCalculationService
                                .calculate(period.trim());

                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of(response.getMessage()),
                                                Optional.of(response)));

        }
}


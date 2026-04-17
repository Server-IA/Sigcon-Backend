package com.sigcon.backend.assets.niif_alerts.interfaces;

import com.sigcon.backend.assets.niif_alerts.application.ApplyNiifCorrectionRequest;
import com.sigcon.backend.assets.niif_alerts.application.CreateAnnualReviewRequest;
import com.sigcon.backend.assets.niif_alerts.application.VerifyNiifRequest;
import com.sigcon.backend.assets.niif_alerts.domain.service.NiifAlertsService;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller REST para verificacion de cumplimiento NIIF, correcciones contables
 * y revisiones anuales de activos fijos.
 * Endpoints para HU-ACT-05, HU-ACT-06, HU-ACT-11, HU-ACT-12, HU-ACT-13, HU-ACT-14.
 */
@RestController
@RequestMapping("/api/v1/niif-alerts")
@RequiredArgsConstructor
@Tag(name = "NIIF Alerts", description = "Verificación de cumplimiento NIIF, correcciones contables y revisiones anuales")
public class NiifAlertsController {

    private final NiifAlertsService niifAlertsService;

    /**
     * RF-05 / ACT-13: Verificar cumplimiento NIIF (6 checks mejorados).
     */
    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('PERM_VIEW_ASSET') or hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Verificar cumplimiento NIIF",
            description = "Analiza activos y genera alertas si se detectan incumplimientos según las reglas NIIF. "
                    + "Incluye 6 verificaciones: vida útil, valor libros, depreciación, valor residual, "
                    + "depreciación acumulada y verificación periódica."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verificación realizada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> verify(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Lista de IDs de activos a verificar."
            )
            @RequestBody VerifyNiifRequest request
    ) {

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Verificación NIIF realizada correctamente"),
                        Optional.of(niifAlertsService.verifyAssets(request))
                )
        );
    }

    /**
     * RF-06 / ACT-11 / ACT-14: Aplicar corrección NIIF.
     * Soporta: REVALUATION, USEFUL_LIFE_ADJUSTMENT, DEPRECIATION_METHOD_CHANGE, IMPAIRMENT_REVERSAL.
     */
    @PostMapping("/correction")
    @PreAuthorize("hasAuthority('PERM_UPDATE_ASSET') or hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Aplicar corrección NIIF",
            description = "Permite aplicar ajustes contables a un activo para cumplir con normas NIIF. "
                    + "Para REVALUATION genera asiento contable al ORI. "
                    + "Para USEFUL_LIFE_ADJUSTMENT recalcula depreciación prospectiva. "
                    + "Para IMPAIRMENT_REVERSAL valida que no exceda valor original."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Corrección aplicada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error en los datos enviados",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "404", description = "Activo no encontrado",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> correction(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos de corrección NIIF a aplicar."
            )
            @RequestBody ApplyNiifCorrectionRequest request
    ) {

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Corrección aplicada correctamente"),
                        Optional.of(niifAlertsService.applyCorrection(request))
                )
        );
    }

    // ───────────────────────────────────────────────────────────────
    // HU-ACT-12: Revisión anual de activos
    // ───────────────────────────────────────────────────────────────

    /**
     * ACT-12: Lista activos elegibles para revisión anual con datos de depreciación.
     *
     * @param fiscalYear año fiscal para la revisión
     * @return lista de activos con depreciación mensual calculada
     */
    @GetMapping("/annual-review/assets")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Listar activos para revisión anual",
            description = "Retorna activos activos elegibles para revisión anual NIC 16, "
                    + "incluyendo vida útil, valor residual, valor en libros y depreciación mensual calculada."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de activos obtenida correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Parámetro inválido",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> listAssetsForReview(
            @Parameter(description = "Año fiscal para la revisión", required = true, example = "2026")
            @RequestParam Integer fiscalYear
    ) {

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Activos para revisión anual obtenidos correctamente"),
                        Optional.of(niifAlertsService.listAssetsForReview(fiscalYear))
                )
        );
    }

    /**
     * ACT-12: Registra una revisión anual de activo (vida útil y/o valor residual).
     *
     * @param request datos de la revisión anual
     * @return resultado de la revisión con datos anteriores y nuevos
     */
    @PostMapping("/annual-review")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(
            summary = "Registrar revisión anual de activo",
            description = "Registra la revisión anual de un activo según NIC 16. "
                    + "Si newUsefulLife o newResidualValue cambian, recalcula la depreciación prospectiva. "
                    + "Si no cambian, se registra como CONFIRMED. Operación idempotente por activo+año."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Revisión registrada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o activo no activo",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "404", description = "Activo no encontrado",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> createAnnualReview(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos de revisión anual: assetId, fiscalYear y opcionalmente newUsefulLife, newResidualValue."
            )
            @Valid @RequestBody CreateAnnualReviewRequest request
    ) {

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Revisión anual procesada correctamente"),
                        Optional.of(niifAlertsService.createAnnualReview(request))
                )
        );
    }
}

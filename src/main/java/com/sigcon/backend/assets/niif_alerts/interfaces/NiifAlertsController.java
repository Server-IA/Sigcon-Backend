package com.sigcon.backend.assets.niif_alerts.interfaces;

import com.sigcon.backend.assets.niif_alerts.application.ApplyNiifCorrectionRequest;
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

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/niif-alerts")
@RequiredArgsConstructor
@Tag(name = "NIIF Alerts", description = "Verificación de cumplimiento NIIF y correcciones contables")
public class NiifAlertsController {

    private final NiifAlertsService niifAlertsService;

    /**
     * RF-05 Verificar cumplimiento NIIF
     */
    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('PERM_VIEW_ASSET') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Verificar cumplimiento NIIF",
            description = "Analiza activos y genera alertas si se detectan incumplimientos según las reglas NIIF."
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
    ){

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Verificación NIIF realizada correctamente"),
                        Optional.of(niifAlertsService.verifyAssets(request))
                )
        );
    }

    /**
     * RF-06 Aplicar corrección NIIF
     */
    @PostMapping("/correction")
    @PreAuthorize("hasAuthority('PERM_UPDATE_ASSET') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Aplicar corrección NIIF",
            description = "Permite aplicar ajustes contables a un activo para cumplir con normas NIIF."
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
    ){

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Corrección aplicada correctamente"),
                        Optional.of(niifAlertsService.applyCorrection(request))
                )
        );
    }
}
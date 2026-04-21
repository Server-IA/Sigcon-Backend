package com.sigcon.backend.integration.interfaces.controller;

import com.sigcon.backend.integration.application.AgroFusionExchangeUpdateDTO;
import com.sigcon.backend.integration.domain.service.AaefMappingException;
import com.sigcon.backend.integration.domain.service.CancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * HU-INT-RF-10: Endpoint del flujo Pull+Diff (anulaciones/correcciones).
 *
 * <p>{@code POST /api/contabilidad/anulaciones} recibe un
 * {@link AgroFusionExchangeUpdateDTO} con el tipo de cambio
 * (CANCELLED | MODIFIED | NEW) y ejecuta la accion correspondiente sobre los
 * asientos contables y documentos generados previamente desde AAEF.
 *
 * <p>Autenticacion: header {@code X-API-Key} (mismo filtro del endpoint /aaef).
 */
@Slf4j
@RestController
@RequestMapping("/api/contabilidad")
@RequiredArgsConstructor
@Tag(name = "Integracion AAEF - Pull+Diff",
     description = "Anulaciones y correcciones Pull+Diff desde AgroFusion (HU-INT-RF-10)")
public class CancellationController {

    private final CancellationService cancellationService;

    @Operation(
        summary = "Procesar anulacion o correccion Pull+Diff",
        description = "Recibe un AgroFusionExchangeUpdate con changeType=CANCELLED/MODIFIED/NEW " +
                      "y ejecuta reversion, correccion o nueva creacion de asientos contables. " +
                      "Requiere X-API-Key."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Actualizacion procesada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Payload invalido o documento no soportado"),
        @ApiResponse(responseCode = "401", description = "X-API-Key ausente o invalida"),
        @ApiResponse(responseCode = "404", description = "originalExchangeId no encontrado")
    })
    @PostMapping(value = "/anulaciones",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> processUpdate(@Valid @RequestBody AgroFusionExchangeUpdateDTO dto) {
        try {
            Map<String, Object> result = cancellationService.processUpdate(dto);
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (AaefMappingException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("errorCode", e.getErrorCode());
            body.put("message", e.getMessage());
            int status = AaefMappingException.ORIGINAL_NOT_FOUND.equals(e.getErrorCode()) ? 404 : 400;
            return ResponseEntity.status(status).body(body);
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            log.error("Error procesando Pull+Diff", e);
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("errorCode", "INTERNAL_ERROR");
            body.put("message", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }
}

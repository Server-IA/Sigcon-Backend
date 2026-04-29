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
        summary = "[DEPRECATED] Procesar anulacion o correccion Pull+Diff",
        description = "DEPRECATED desde 2026-04: AgroFusion ahora envia el envelope " +
                      "AgroFusionExchangeUpdate al endpoint unificado POST /api/contabilidad/aaef. " +
                      "Este endpoint legacy queda activo solo para retrocompatibilidad y se " +
                      "removera en una version futura. Use /api/contabilidad/aaef.",
        deprecated = true
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
    @Deprecated
    public ResponseEntity<?> processUpdate(@Valid @RequestBody AgroFusionExchangeUpdateDTO dto) {
        // Spec AAEF Bloque W: log de warning explicito al invocar el endpoint
        // legacy para que quede traza forense de quien sigue usandolo. Cuando
        // se confirme que AgroFusion 100% migrado al envelope unificado en
        // /aaef, se elimina este controller.
        log.warn("DEPRECATED endpoint POST /api/contabilidad/anulaciones invocado. "
                + "AgroFusion debe migrar a POST /api/contabilidad/aaef con envelope "
                + "AgroFusionExchangeUpdate. originalExchangeId={}, documentId={}",
                dto.getOriginalExchangeId(), dto.getDocumentId());
        try {
            Map<String, Object> result = cancellationService.processUpdate(dto);
            result.put("success", true);
            // Header X-Deprecated-Endpoint para que el cliente vea facilmente
            // que esta consumiendo un endpoint deprecated.
            return ResponseEntity.ok()
                    .header("X-Deprecated-Endpoint", "true")
                    .header("X-Deprecation-Notice",
                            "Use POST /api/contabilidad/aaef con envelope AgroFusionExchangeUpdate")
                    .body(result);
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

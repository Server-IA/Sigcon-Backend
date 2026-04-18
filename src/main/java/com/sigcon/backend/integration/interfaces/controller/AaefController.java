package com.sigcon.backend.integration.interfaces.controller;

import com.sigcon.backend.integration.application.AaefBatchRequest;
import com.sigcon.backend.integration.application.IntegrationBatchDTO;
import com.sigcon.backend.integration.domain.service.AaefReceiverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-INT-RF-01: Endpoint principal de recepcion de lotes AAEF.
 *
 * <p>Recibe un lote AAEF (JSON) de AgroFusion, valida estructura e idempotencia,
 * persiste en {@code integration_batches} y responde HTTP 202 Accepted inmediatamente.
 *
 * <p>El procesamiento contable (generacion de JournalEntries) se hace asincrono
 * en Fase 2 ({@code AaefBatchProcessor}). Por ahora el lote queda en estado
 * {@code RECEIVED} para procesamiento posterior.
 *
 * <p>Autenticacion: header {@code X-API-Key} (ver {@code ApiKeyFilter}).
 */
@Slf4j
@RestController
@RequestMapping("/api/contabilidad")
@RequiredArgsConstructor
@Tag(name = "Integracion AAEF - Recepcion",
     description = "Recepcion de lotes AAEF desde AgroFusion (RF-INT-12, HU-INT-RF-01 a 04)")
public class AaefController {

    private final AaefReceiverService receiverService;

    @Operation(
        summary = "Recibir lote AAEF",
        description = "Recibe un lote JSON en formato AAEF v1.0 (RF-INT-13). Valida esquema " +
                      "y retorna HTTP 202 Accepted inmediatamente. El procesamiento se hace " +
                      "asincrono. Requiere header X-API-Key valido."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Lote aceptado y encolado para procesamiento"),
        @ApiResponse(responseCode = "400", description = "JSON malformado o esquema AAEF invalido"),
        @ApiResponse(responseCode = "401", description = "X-API-Key ausente o invalida"),
        @ApiResponse(responseCode = "409", description = "exchangeId ya procesado (duplicado)"),
        @ApiResponse(responseCode = "413", description = "Lote excede 20MB"),
        @ApiResponse(responseCode = "415", description = "Content-Type no es application/json")
    })
    @PostMapping(value = "/aaef", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receive(@Valid @RequestBody AaefBatchRequest batch,
                                     BindingResult bindingResult) {
        // 1. Validaciones de Bean Validation (antes de tocar el service)
        if (bindingResult.hasErrors()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 400);
            body.put("error", "Validacion de esquema AAEF");
            body.put("message", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return ResponseEntity.badRequest().body(body);
        }

        try {
            IntegrationBatchDTO saved = receiverService.receive(batch);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("exchangeId", saved.getExchangeId());
            body.put("batchId", saved.getId());
            body.put("status", saved.getStatus());
            body.put("receivedAt", saved.getReceivedAt());
            return ResponseEntity.accepted().body(body);

        } catch (AaefReceiverService.ValidationException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 400);
            body.put("error", "Validacion de esquema AAEF");
            body.put("errors", e.getErrors());
            return ResponseEntity.badRequest().body(body);

        } catch (AaefReceiverService.DuplicateBatchException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 409);
            body.put("error", "Lote duplicado");
            body.put("message", e.getMessage());
            body.put("existingBatchId", e.getExistingBatchId());
            return ResponseEntity.status(409).body(body);

        } catch (Exception e) {
            log.error("Error inesperado recibiendo lote AAEF", e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 500);
            body.put("error", "Error interno");
            body.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(body);
        }
    }
}

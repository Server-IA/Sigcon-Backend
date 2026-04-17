package com.sigcon.backend.integration.interfaces.controller;

import com.sigcon.backend.integration.application.IntegrationBatchDTO;
import com.sigcon.backend.integration.domain.service.AaefReceiverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HU-INT-RF-09: Consulta de estado de una transferencia.
 *
 * <p>AgroFusion usa {@code GET /api/contabilidad/transferencias/{id}} para
 * verificar en cualquier momento el estado de un lote procesado (RF-INT-12 R03).
 *
 * <p>En Fase 1 el {@code id} es el {@code batchId} interno de SIGCON. En Fase 2
 * se añadira consulta por {@code accountingEntryId}.
 */
@RestController
@RequestMapping("/api/contabilidad")
@RequiredArgsConstructor
@Tag(name = "Integracion AAEF - Consulta de estado",
     description = "Consulta de estado de transferencias AAEF (HU-INT-RF-09)")
public class TransferStatusController {

    private final AaefReceiverService receiverService;

    @Operation(
        summary = "Consultar estado de lote AAEF",
        description = "Retorna metadata, summary y estado actual de un lote previamente recibido. " +
                      "Usa el id interno del batch. Requiere X-API-Key."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado retornado"),
        @ApiResponse(responseCode = "401", description = "X-API-Key invalida"),
        @ApiResponse(responseCode = "404", description = "batchId no encontrado")
    })
    @GetMapping("/transferencias/{id}")
    public ResponseEntity<?> getStatus(@PathVariable Long id) {
        Optional<IntegrationBatchDTO> result = receiverService.findById(id);
        if (result.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 404);
            body.put("error", "Transferencia no encontrada");
            body.put("message", "No existe un batch con id=" + id);
            return ResponseEntity.status(404).body(body);
        }
        return ResponseEntity.ok(result.get());
    }
}

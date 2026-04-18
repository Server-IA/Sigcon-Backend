package com.sigcon.backend.integration.interfaces.controller;

import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import com.sigcon.backend.integration.domain.service.AaefMappingException;
import com.sigcon.backend.integration.domain.service.BatchQueryService;
import com.sigcon.backend.integration.domain.service.TransferHistoryService;
import com.sigcon.backend.integration.domain.service.TransferRetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * HU-INT-RF-14 y HU-INT-RF-15: endpoints de gestion de lotes/transfers para el
 * frontend de integracion. Permite al administrador contable monitorear,
 * auditar y reintentar documentos fallidos.
 */
@Slf4j
@RestController
@RequestMapping("/api/contabilidad")
@RequiredArgsConstructor
@Tag(name = "Integracion AAEF - Gestion (frontend)",
     description = "Consulta paginada, detalle, descarga JSON y reintentos (HU-INT-RF-14/15)")
public class BatchManagementController {

    private final BatchQueryService batchQueryService;
    private final TransferRetryService transferRetryService;
    private final TransferHistoryService transferHistoryService;

    @Operation(
        summary = "Listar lotes AAEF con filtros (HU-INT-RF-14 E1)",
        description = "Retorna lotes paginados y filtrados por sistema origen, estado, rango "
                    + "de fechas y opcion 'solo con fallidos' (E4). Ordenamiento por fecha "
                    + "de recepcion descendente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado paginado (content + page + totalPages)"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/lotes")
    public ResponseEntity<?> listBatches(
            @Parameter(description = "Sistema origen (Disriego, Sigma, AgroFusion)", example = "Disriego")
            @RequestParam(required = false) String sourceSystemId,
            @Parameter(description = "Estado del lote", example = "PROCESSED")
            @RequestParam(required = false) BatchStatus status,
            @Parameter(description = "Fecha desde (yyyy-MM-dd)", example = "2026-04-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fecha hasta (yyyy-MM-dd)", example = "2026-04-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Solo lotes con al menos 1 documento fallido", example = "false")
            @RequestParam(defaultValue = "false") boolean onlyWithFailed,
            @Parameter(description = "Pagina (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de pagina (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(batchQueryService.listBatches(
                sourceSystemId, status, from, to, onlyWithFailed, page, size));
    }

    @Operation(
        summary = "Detalle de lote AAEF (HU-INT-RF-14 E2)",
        description = "Retorna metadata completa + summary + lista de transfers con estado "
                    + "individual y accountingEntryId cuando aplique.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle completo del lote"),
        @ApiResponse(responseCode = "400", description = "Lote inexistente"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/lotes/{id}")
    public ResponseEntity<?> batchDetail(
            @Parameter(description = "ID interno del lote", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(batchQueryService.getBatchDetail(id));
    }

    @Operation(
        summary = "Descargar payload JSON original (HU-INT-RF-14 E3)",
        description = "Retorna el JSON exacto recibido desde AgroFusion para auditoria.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "JSON original (application/json)"),
        @ApiResponse(responseCode = "400", description = "Lote inexistente"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping(value = "/lotes/{id}/payload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> downloadPayload(
            @Parameter(description = "ID interno del lote", example = "1")
            @PathVariable Long id) {
        String json = batchQueryService.getPayloadJson(id);
        byte[] body = (json != null ? json : "{}").getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=aaef-batch-" + id + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @Operation(
        summary = "Reintentar transfer fallido (HU-INT-RF-15)",
        description = "Crea un batch 'retry' con el documento original y dispara reprocesamiento. "
                    + "Solo permitido si retryAllowed=true y status=FAILED/RETRYING. "
                    + "Incrementa retry_count del transfer original (E4).")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "Reintento disparado (retorna newBatchId y retryCount)"),
        @ApiResponse(responseCode = "400",
                description = "Transfer inexistente, retryAllowed=false (E2), o estado no FAILED"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PostMapping("/transferencias/{id}/retry")
    public ResponseEntity<?> retry(
            @Parameter(description = "ID del transfer a reintentar", example = "1")
            @PathVariable Long id,
            @Parameter(description = "Nota opcional del administrador",
                       example = "Periodo reabierto, reintentando")
            @RequestParam(required = false) String note) {
        try {
            return ResponseEntity.ok(transferRetryService.retry(id, note));
        } catch (AaefMappingException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("errorCode", e.getErrorCode());
            body.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        } catch (IllegalStateException | IllegalArgumentException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    @Operation(
        summary = "Historial de intentos de un transfer (HU-INT-RF-15 E4)",
        description = "Lista cronologica de cada procesamiento del transfer (intento inicial + "
                    + "retries). Cada entrada incluye: numero de intento, resultado "
                    + "(SUCCESS/FAILED/RETRYING), error si fallo, quien y cuando lo gatillo, "
                    + "nota del usuario si aplica, y newBatchId si se origino un retry.")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "Lista cronologica de intentos (puede estar vacia para transfers nuevos)"),
        @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/transferencias/{id}/history")
    public ResponseEntity<?> getTransferHistory(
            @Parameter(description = "ID del transfer", example = "1")
            @PathVariable Long id) {
        Map<String, Object> body = new HashMap<>();
        body.put("transferId", id);
        body.put("history", transferHistoryService.getHistory(id));
        return ResponseEntity.ok(body);
    }
}

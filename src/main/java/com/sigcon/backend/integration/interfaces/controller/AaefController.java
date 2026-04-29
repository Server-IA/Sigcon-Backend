package com.sigcon.backend.integration.interfaces.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.integration.application.AaefBatchRequest;
import com.sigcon.backend.integration.application.AgroFusionExchangeUpdateDTO;
import com.sigcon.backend.integration.application.IntegrationBatchDTO;
import com.sigcon.backend.integration.domain.model.IntegrationIdempotencyKey;
import com.sigcon.backend.integration.domain.repository.IntegrationIdempotencyKeyRepository;
import com.sigcon.backend.integration.domain.service.AaefMappingException;
import com.sigcon.backend.integration.domain.service.AaefReceiverService;
import com.sigcon.backend.integration.domain.service.CancellationService;
import com.sigcon.backend.integration.domain.service.CompanyNotFoundException;
import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-INT-RF-01 + HU-INT-RF-10: Endpoint unificado de recepcion AAEF.
 *
 * <p>{@code POST /api/contabilidad/aaef} recibe DOS tipos de envelope JSON segun
 * RF-INT-12 + RF-INT-14 (Pull+Diff):
 *
 * <ol>
 *   <li><b>Batch AAEF inicial</b>: root keys {@code metadata}, {@code summary},
 *       {@code invoices[]}, {@code transactions[]}. Se delega a
 *       {@link AaefReceiverService#receive}.</li>
 *   <li><b>AgroFusionExchangeUpdate (Pull+Diff)</b>: root key
 *       {@code AgroFusionExchangeUpdate}. Se delega a {@link CancellationService}
 *       por cada documento en {@code Changes.Invoices[]} y {@code Changes.Transactions[]}.</li>
 * </ol>
 *
 * <p>En ambos envelopes, el NIT destino vive en
 * {@code metadata.SourceSystem.SystemNIT} (batch) o
 * {@code AgroFusionExchangeUpdate.Metadata.SourceSystem.SystemNIT} (update).
 * El envelope se resuelve a {@code company_id} consultando {@link CompanyRepository}.
 * Si el NIT no corresponde a ninguna empresa activa, se rechaza con
 * {@code errorCode=COMPANY_NOT_FOUND} (HTTP 400).
 *
 * <p>Autenticacion: header {@code X-API-Key} UNICA global (ver {@code ApiKeyFilter}).
 * El enrutamiento tenant se hace despues por NIT.
 */
@Slf4j
@RestController
@RequestMapping("/api/contabilidad")
@RequiredArgsConstructor
@Tag(name = "Integracion AAEF - Recepcion",
     description = "Recepcion de lotes AAEF desde AgroFusion (RF-INT-12 + RF-INT-14 Pull+Diff)")
public class AaefController {

    private final AaefReceiverService receiverService;
    private final CancellationService cancellationService;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    // Spec AAEF Bloque W: idempotencia para Pull+Diff. Registramos el
    // ExchangeId del update con standardVersion="UPDATE-1.0" para que NO
    // colisione con la del lote inicial pero si detecte reenvios duplicados.
    private final IntegrationIdempotencyKeyRepository idempotencyKeyRepository;
    private static final String UPDATE_STANDARD_VERSION = "UPDATE-1.0";

    @Operation(
        summary = "Recibir lote AAEF o AgroFusionExchangeUpdate",
        description = "Endpoint unificado para recepcion de lotes iniciales y actualizaciones "
                    + "diferenciales (Pull+Diff). Detecta automaticamente el tipo de envelope "
                    + "y rutea. Resuelve empresa destino por metadata.SourceSystem.SystemNIT "
                    + "y valida idempotencia. Retorna HTTP 202 Accepted para batches iniciales "
                    + "y HTTP 200 con resumen para actualizaciones."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Actualizacion Pull+Diff procesada"),
        @ApiResponse(responseCode = "202", description = "Batch aceptado y encolado"),
        @ApiResponse(responseCode = "400", description = "JSON malformado, esquema invalido o NIT no encontrado (COMPANY_NOT_FOUND)"),
        @ApiResponse(responseCode = "401", description = "X-API-Key ausente o invalida"),
        @ApiResponse(responseCode = "409", description = "exchangeId ya procesado"),
        @ApiResponse(responseCode = "413", description = "Lote excede 20MB"),
        @ApiResponse(responseCode = "415", description = "Content-Type no es application/json")
    })
    @PostMapping(value = "/aaef", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receive(@RequestBody JsonNode raw) {
        if (raw == null || raw.isNull()) {
            return badRequest("Payload vacio");
        }

        // Detectar envelope
        if (raw.has("AgroFusionExchangeUpdate")) {
            return handleUpdateEnvelope(raw.get("AgroFusionExchangeUpdate"));
        }
        if (raw.has("metadata") || raw.has("Metadata")) {
            return handleBatch(raw);
        }
        return badRequest("Envelope no reconocido. Se esperaba 'metadata' (batch inicial) "
                        + "o 'AgroFusionExchangeUpdate' (Pull+Diff).");
    }

    // ------------------------------------------------------------------
    // Batch inicial AAEF
    // ------------------------------------------------------------------
    private ResponseEntity<?> handleBatch(JsonNode raw) {
        AaefBatchRequest batch;
        try {
            batch = objectMapper.treeToValue(raw, AaefBatchRequest.class);
        } catch (Exception e) {
            return badRequest("JSON AAEF malformado: " + e.getMessage());
        }

        // Validaciones Bean Validation (ya que no usamos @Valid directo)
        var violations = validator.validate(batch);
        if (!violations.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 400);
            body.put("error", "Validacion de esquema AAEF");
            body.put("message", violations.iterator().next().getMessage());
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

        } catch (CompanyNotFoundException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 400);
            body.put("errorCode", e.getErrorCode());
            body.put("nit", e.getNit());
            body.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(body);

        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
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

    // ------------------------------------------------------------------
    // Pull+Diff AgroFusionExchangeUpdate
    // ------------------------------------------------------------------
    private ResponseEntity<?> handleUpdateEnvelope(JsonNode envelope) {
        JsonNode metadata = envelope.get("Metadata");
        if (metadata == null) {
            return badRequest("AgroFusionExchangeUpdate.Metadata es obligatorio");
        }

        // RF-INT-12: resolver empresa destino por NIT del SourceSystem.
        JsonNode srcSystem = metadata.get("SourceSystem");
        String nit = (srcSystem != null && srcSystem.has("SystemNIT"))
                ? srcSystem.get("SystemNIT").asText()
                : null;
        if (nit == null || nit.isBlank()) {
            return badRequest("AgroFusionExchangeUpdate.Metadata.SourceSystem.SystemNIT es obligatorio");
        }

        Company company = companyRepository.findByNitAndDeletedAtIsNull(nit).orElse(null);
        if (company == null || company.getStatus() != Company.CompanyStatus.ACTIVE) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 400);
            body.put("errorCode", CompanyNotFoundException.ERROR_CODE);
            body.put("nit", nit);
            body.put("message", "El NIT '" + nit + "' no corresponde a ninguna empresa activa en SIGCON");
            return ResponseEntity.badRequest().body(body);
        }

        TenantContext.setCompanyId(company.getId());
        log.info("Pull+Diff: empresa destino resuelta por NIT={} -> companyId={}",
                nit, company.getId());

        String originalExchangeId = metadata.has("OriginalExchangeId")
                ? metadata.get("OriginalExchangeId").asText()
                : null;
        String updateExchangeId = metadata.has("ExchangeId")
                ? metadata.get("ExchangeId").asText()
                : null;

        // Spec AAEF Bloque W: idempotencia del Pull+Diff. Si ya recibimos un
        // update con el mismo ExchangeId (sin importar el lote padre), rechazar
        // con HTTP 409 para que AgroFusion no procese dos veces. Se usa la
        // standardVersion "UPDATE-1.0" para no colisionar con la del lote
        // inicial.
        if (updateExchangeId != null && !updateExchangeId.isBlank()) {
            var existing = idempotencyKeyRepository
                    .findByExchangeIdAndStandardVersion(updateExchangeId, UPDATE_STANDARD_VERSION)
                    .orElse(null);
            if (existing != null) {
                Map<String, Object> dup = new LinkedHashMap<>();
                dup.put("success", false);
                dup.put("code", 409);
                dup.put("error", "Update duplicado");
                dup.put("message", "Este AgroFusionExchangeUpdate.Metadata.ExchangeId ya fue procesado");
                dup.put("exchangeId", updateExchangeId);
                dup.put("existingBatchId", existing.getBatchId());
                return ResponseEntity.status(409).body(dup);
            }
            // Registrar la llave para bloquear futuros duplicados.
            idempotencyKeyRepository.save(IntegrationIdempotencyKey.builder()
                    .exchangeId(updateExchangeId)
                    .standardVersion(UPDATE_STANDARD_VERSION)
                    .firstReceivedAt(java.time.LocalDateTime.now())
                    .lastAttemptAt(java.time.LocalDateTime.now())
                    .attemptCount(1)
                    .build());
        }

        // Iterar Changes.Invoices[] y Changes.Transactions[]
        JsonNode changes = envelope.get("Changes");
        List<Map<String, Object>> processed = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        processChangeGroup(changes, "Invoices", "INVOICE", originalExchangeId, processed, failed);
        processChangeGroup(changes, "Transactions", "TRANSACTION", originalExchangeId, processed, failed);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", failed.isEmpty());
        body.put("OriginalExchangeId", updateExchangeId != null ? updateExchangeId : originalExchangeId);
        body.put("Status", failed.isEmpty() ? "ACCEPTED" : (processed.isEmpty() ? "REJECTED" : "PARTIAL"));
        body.put("ProcessedDocuments", processed);
        body.put("FailedDocuments", failed);
        return ResponseEntity.ok(body);
    }

    private void processChangeGroup(JsonNode changes, String groupKey, String documentType,
                                    String originalExchangeId,
                                    List<Map<String, Object>> processed,
                                    List<Map<String, Object>> failed) {
        if (changes == null || !changes.has(groupKey)) return;
        JsonNode arr = changes.get(groupKey);
        if (!arr.isArray()) return;

        for (JsonNode node : arr) {
            String rawChangeType = node.has("changeType") ? node.get("changeType").asText()
                                  : node.has("ChangeType") ? node.get("ChangeType").asText() : null;
            if (rawChangeType == null) {
                failed.add(Map.of("DocumentId", documentIdOf(node),
                                  "ErrorCode", "MISSING_CHANGE_TYPE",
                                  "ErrorMessage", "El documento no tiene changeType"));
                continue;
            }

            // NO_CHANGE: no-op, solo log de auditoria
            if ("NO_CHANGE".equalsIgnoreCase(rawChangeType)) {
                processed.add(Map.of("DocumentId", documentIdOf(node),
                                     "DocumentType", documentType,
                                     "Status", "NO_CHANGE"));
                continue;
            }

            // CORRECTION se mapea a MODIFIED (reversa + recreacion), que es el mismo
            // patron contable de inmutabilidad. Se documenta en el log.
            String mappedChangeType;
            if ("CORRECTION".equalsIgnoreCase(rawChangeType)) {
                mappedChangeType = "MODIFIED";
                log.info("Pull+Diff: CORRECTION mapeado a MODIFIED para doc {}", documentIdOf(node));
            } else if ("CANCELLED".equalsIgnoreCase(rawChangeType)
                    || "MODIFIED".equalsIgnoreCase(rawChangeType)
                    || "NEW".equalsIgnoreCase(rawChangeType)) {
                mappedChangeType = rawChangeType.toUpperCase();
            } else {
                failed.add(Map.of("DocumentId", documentIdOf(node),
                                  "ErrorCode", "UNSUPPORTED_CHANGE_TYPE",
                                  "ErrorMessage", "changeType no soportado: " + rawChangeType));
                continue;
            }

            // Construir DTO y delegar
            AgroFusionExchangeUpdateDTO dto = AgroFusionExchangeUpdateDTO.builder()
                    .originalExchangeId(originalExchangeId)
                    .changeType(AgroFusionExchangeUpdateDTO.ChangeType.valueOf(mappedChangeType))
                    .documentType(documentType)
                    .documentId(documentIdOf(node))
                    .document(node)
                    .reason(node.has("ChangeMetadata") && node.get("ChangeMetadata").has("Reason")
                            ? node.get("ChangeMetadata").get("Reason").asText()
                            : null)
                    .build();

            try {
                Map<String, Object> result = cancellationService.processUpdate(dto);
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("DocumentId", dto.getDocumentId());
                line.put("DocumentType", documentType);
                line.put("Status", "PROCESSED");
                line.putAll(result);
                processed.add(line);
            } catch (AaefMappingException e) {
                failed.add(Map.of("DocumentId", dto.getDocumentId(),
                                  "ErrorCode", e.getErrorCode(),
                                  "ErrorMessage", e.getMessage()));
            } catch (Exception e) {
                log.error("Pull+Diff: error procesando {}", dto.getDocumentId(), e);
                failed.add(Map.of("DocumentId", dto.getDocumentId(),
                                  "ErrorCode", "INTERNAL_ERROR",
                                  "ErrorMessage", e.getMessage() != null ? e.getMessage() : "error interno"));
            }
        }
    }

    private String documentIdOf(JsonNode node) {
        if (node.has("DocumentId")) return node.get("DocumentId").asText();
        if (node.has("Header") && node.get("Header").has("DocumentId")) {
            return node.get("Header").get("DocumentId").asText();
        }
        return "UNKNOWN";
    }

    private ResponseEntity<?> badRequest(String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", 400);
        body.put("error", "Payload invalido");
        body.put("message", msg);
        return ResponseEntity.badRequest().body(body);
    }
}

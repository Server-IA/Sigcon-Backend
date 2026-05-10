package com.sigcon.backend.integration.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.integration.application.AaefBatchRequest;
import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.IntegrationTransfer;
import com.sigcon.backend.integration.domain.model.enums.TransferStatus;
import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import com.sigcon.backend.integration.domain.repository.IntegrationTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * HU-INT-RF-15: reintento manual de transfers fallidos desde el frontend.
 *
 * <p>Dado un transfer FAILED con {@code retryAllowed=true}, este servicio:
 * <ol>
 *   <li>Localiza el documento original dentro del {@code payload_json} del batch
 *       por su {@code documentId}</li>
 *   <li>Construye un mini-batch "retry" con ese unico documento y lo persiste</li>
 *   <li>Incrementa {@code retry_count} del transfer original</li>
 *   <li>Dispara el procesamiento async via {@link BatchReceivedEvent}</li>
 * </ol>
 *
 * <p>Si el documento se procesa correctamente, un nuevo transfer queda como
 * PROCESSED en el batch de retry. El transfer original conserva su historial
 * pero se actualiza {@code retry_count} para trazabilidad (HU-INT-RF-15 E4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferRetryService {

    private final IntegrationTransferRepository transferRepository;
    private final IntegrationBatchRepository batchRepository;
    private final ObjectMapper objectMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final TransferHistoryService historyService;

    /**
     * Reintenta un transfer fallido.
     *
     * @return mapa con newBatchId y estado
     * @throws IllegalStateException si retry_allowed=false o status != FAILED
     * @throws AaefMappingException si el documento no se encuentra en el payload original
     */
    @Transactional
    public Map<String, Object> retry(Long transferId, String userNote) {
        IntegrationTransfer t = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer no encontrado"));

        // HU-INT-RF-15 E2: retry_allowed=false bloquea
        if (!Boolean.TRUE.equals(t.getRetryAllowed())) {
            throw new IllegalStateException(
                    "Este error no permite reintento. Solicite nuevo envío a AgroFusion");
        }
        if (t.getTransferStatus() != TransferStatus.FAILED
                && t.getTransferStatus() != TransferStatus.RETRYING) {
            throw new IllegalStateException(
                    "Solo se pueden reintentar transfers en estado FAILED (actual: "
                    + t.getTransferStatus() + ")");
        }

        IntegrationBatch original = t.getBatch();
        if (original == null || original.getPayloadJson() == null) {
            throw new IllegalStateException("El lote original no tiene payload");
        }

        // Localizar el documento en el payload original
        try {
            AaefBatchRequest payload = objectMapper.readValue(
                    original.getPayloadJson(), AaefBatchRequest.class);
            JsonNode documentNode = findDocumentByDocumentId(payload, t.getDocumentId());
            if (documentNode == null) {
                throw new AaefMappingException(
                        AaefMappingException.MAPPING_ERROR,
                        "Documento " + t.getDocumentId() + " no encontrado en payload original");
            }

            // Marcar transfer original como RETRYING + incrementar contador
            t.setTransferStatus(TransferStatus.RETRYING);
            t.setRetryCount((t.getRetryCount() == null ? 0 : t.getRetryCount()) + 1);
            t.setProcessedAt(LocalDateTime.now());
            if (userNote != null && !userNote.isBlank()) {
                String prev = t.getErrorMessage() != null ? t.getErrorMessage() : "";
                t.setErrorMessage(prev + " | Retry: " + userNote);
            }
            transferRepository.save(t);

            // Construir mini-batch de retry
            Long newBatchId = buildRetryBatch(original, documentNode, t);

            // HU-INT-RF-15 E4: registrar el intento de retry en el historial.
            // El resultado real (SUCCESS/FAILED) lo registrara el AaefBatchProcessor
            // cuando procese el nuevo batch sintetico.
            historyService.recordRetryAttempt(
                    transferId,
                    t.getRetryCount(),
                    historyService.currentUsername(),
                    userNote,
                    newBatchId);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("transferId", transferId);
            resp.put("newBatchId", newBatchId);
            resp.put("retryCount", t.getRetryCount());
            return resp;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "Error leyendo payload original: " + e.getMessage());
        }
    }

    private JsonNode findDocumentByDocumentId(AaefBatchRequest payload, String documentId) {
        if (documentId == null) return null;
        // Nota: el grupo "payroll" del estandar AAEF original fue desestimado del
        // alcance, por lo que solo se busca en invoices y transactions.
        java.util.List<java.util.List<JsonNode>> groups = java.util.Arrays.asList(
                payload.getInvoices(), payload.getTransactions());
        for (java.util.List<JsonNode> arr : groups) {
            if (arr == null) continue;
            for (JsonNode doc : arr) {
                // Invoices tienen DocumentId dentro de Header; Transactions lo tienen en raiz
                if (doc.has("DocumentId") && documentId.equals(doc.get("DocumentId").asText())) {
                    return doc;
                }
                if (doc.has("Header") && doc.get("Header").has("DocumentId")
                        && documentId.equals(doc.get("Header").get("DocumentId").asText())) {
                    return doc;
                }
            }
        }
        return null;
    }

    private Long buildRetryBatch(IntegrationBatch original, JsonNode documentNode,
                                   IntegrationTransfer sourceTransfer) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode retryPayload = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode metadata = objectMapper.createObjectNode();
            String newExchangeId = original.getExchangeId() + "-RETRY-" + sourceTransfer.getRetryCount();
            metadata.put("ExchangeId", newExchangeId);
            metadata.put("StandardVersion", "1.0");
            // QA Bloque PA Bug 70 (HU-INT-13, 2026-05-09): GeneratedAt ahora es
            // fecha sola (yyyy-MM-dd) por compatibilidad con AAEF v1.1.
            metadata.put("GeneratedAt", java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
            com.fasterxml.jackson.databind.node.ObjectNode srcSys = objectMapper.createObjectNode();
            srcSys.put("SystemId", "SIGCON-Retry");
            srcSys.put("Version", "1.0");
            metadata.set("SourceSystem", srcSys);
            retryPayload.set("metadata", metadata);

            com.fasterxml.jackson.databind.node.ObjectNode summary = objectMapper.createObjectNode();
            summary.put("TotalDocuments", 1);
            retryPayload.set("summary", summary);

            // Nota: el grupo "payroll" del estandar AAEF original fue desestimado del
            // alcance del proyecto. Solo se construye retry para INVOICE y TRANSACTION.
            com.fasterxml.jackson.databind.node.ArrayNode invoices = objectMapper.createArrayNode();
            com.fasterxml.jackson.databind.node.ArrayNode transactions = objectMapper.createArrayNode();
            String docType = sourceTransfer.getDocumentType() != null
                    ? sourceTransfer.getDocumentType().name() : "INVOICE";
            switch (docType) {
                case "INVOICE": invoices.add(documentNode); break;
                case "TRANSACTION": transactions.add(documentNode); break;
                default: invoices.add(documentNode);
            }
            retryPayload.set("invoices", invoices);
            retryPayload.set("transactions", transactions);

            IntegrationBatch retryBatch = IntegrationBatch.builder()
                    .exchangeId(newExchangeId)
                    .standardVersion("1.0")
                    .sourceSystemId("SIGCON-Retry")
                    .sourceSystemName("Reintento manual HU-INT-RF-15")
                    .receivedAt(LocalDateTime.now())
                    .totalDocuments(1)
                    .totalInvoices("INVOICE".equals(docType) ? 1 : 0)
                    .totalTransactions("TRANSACTION".equals(docType) ? 1 : 0)
                    .status(com.sigcon.backend.integration.domain.model.enums.BatchStatus.RECEIVED)
                    .payloadJson(objectMapper.writeValueAsString(retryPayload))
                    .build();
            retryBatch = batchRepository.save(retryBatch);

            // Disparar procesamiento post-commit
            eventPublisher.publishEvent(new BatchReceivedEvent(this, retryBatch.getId()));
            return retryBatch.getId();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "Error serializando batch de retry: " + e.getMessage());
        }
    }
}

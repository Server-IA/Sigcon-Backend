package com.sigcon.backend.integration.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.integration.application.AgroFusionExchangeUpdateDTO;
import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.IntegrationTransfer;
import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import com.sigcon.backend.integration.domain.model.enums.TransferStatus;
import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import com.sigcon.backend.integration.domain.repository.IntegrationTransferRepository;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-INT-RF-10: Procesador del flujo Pull+Diff de AgroFusion.
 *
 * <p>Recibe {@link AgroFusionExchangeUpdateDTO} desde {@code POST /api/contabilidad/anulaciones}
 * y ejecuta la accion correspondiente:
 * <ul>
 *   <li><b>CANCELLED</b>: reversa el/los asientos contables del lote original.
 *       Si el JE esta en DRAFT lo postea primero para permitir reversal.
 *       Marca las facturas afectadas como VOIDED.</li>
 *   <li><b>MODIFIED</b>: reversa el original y re-procesa el documento actualizado
 *       como uno nuevo (equivalente a cancelar + recrear). Cumple con principio
 *       contable de inmutabilidad: no se editan JEs contabilizados.</li>
 *   <li><b>NEW</b>: crea un mini-batch en {@code integration_batches} con el
 *       documento y delega a {@link AaefBatchProcessor} para procesarlo.</li>
 * </ul>
 *
 * <p>Errores estandar:
 * <ul>
 *   <li>{@code ORIGINAL_NOT_FOUND}: no existe lote con el exchangeId indicado.</li>
 *   <li>{@code UNSUPPORTED_TYPE}: documentType no soportado.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationService {

    private final IntegrationBatchRepository batchRepository;
    private final IntegrationTransferRepository transferRepository;
    private final JournalEntryService journalEntryService;
    private final JournalEntryRepository journalEntryRepository;
    private final InvoiceRepository invoiceRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final AaefBatchProcessor batchProcessor;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private static final String STANDARD_VERSION = "1.0";

    /**
     * Ejecuta la accion Pull+Diff correspondiente al tipo de cambio.
     *
     * @param dto payload recibido desde AgroFusion
     * @return mapa con resultado: {status, changeType, affectedEntries[], newBatchId?}
     */
    @Transactional
    public Map<String, Object> processUpdate(AgroFusionExchangeUpdateDTO dto) {
        log.info("Pull+Diff: changeType={}, originalExchangeId={}, documentId={}",
                dto.getChangeType(), dto.getOriginalExchangeId(), dto.getDocumentId());

        switch (dto.getChangeType()) {
            case CANCELLED:
                return processCancel(dto);
            case MODIFIED:
                return processModify(dto);
            case NEW:
                return processNew(dto);
            default:
                throw new AaefMappingException(
                        AaefMappingException.UNSUPPORTED_TYPE,
                        "changeType no soportado: " + dto.getChangeType());
        }
    }

    // ============================================================
    // CANCELLED (HU-INT-RF-10 E1)
    // ============================================================

    private Map<String, Object> processCancel(AgroFusionExchangeUpdateDTO dto) {
        IntegrationBatch batch = findOriginalBatch(dto.getOriginalExchangeId());
        List<IntegrationTransfer> transfers = transferRepository.findByBatch_IdAndDeletedAtIsNull(batch.getId());

        List<Long> reversedEntries = new java.util.ArrayList<>();
        for (IntegrationTransfer t : transfers) {
            if (t.getAccountingEntryId() == null) continue;
            Long jeId = t.getAccountingEntryId();

            // Reversar JE (postear si esta en DRAFT)
            Long reversalId = reverseOrDelete(jeId, dto.getReason());
            reversedEntries.add(reversalId != null ? reversalId : jeId);

            // Marcar invoice/salesInvoice como VOIDED
            voidLinkedInvoice(t);

            // Marcar transfer como reversado
            t.setTransferStatus(TransferStatus.FAILED);
            t.setErrorCode("CANCELLED_BY_AGROFUSION");
            t.setErrorMessage("Cancelado via Pull+Diff: " + (dto.getReason() != null ? dto.getReason() : "sin motivo"));
            t.setProcessedAt(LocalDateTime.now());
            transferRepository.save(t);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "CANCELLED");
        result.put("originalExchangeId", dto.getOriginalExchangeId());
        result.put("affectedEntries", reversedEntries);
        return result;
    }

    /**
     * Reversa un JE. Si esta en DRAFT lo postea primero para permitir reversal.
     * Retorna el id del asiento de reversion, o {@code null} si no pudo procesar.
     */
    private Long reverseOrDelete(Long jeId, String reason) {
        JournalEntry je = journalEntryRepository.findById(jeId).orElse(null);
        if (je == null) {
            log.warn("JournalEntry {} no encontrado, no se puede reversar", jeId);
            return null;
        }
        if (je.getStatus() == JournalEntryStatus.DRAFT) {
            // Postear primero para poder reversar (HU-CG-08)
            journalEntryService.postEntry(jeId);
        }
        if (je.getStatus() == JournalEntryStatus.REVERSED) {
            log.info("JE {} ya esta REVERSED, se omite", jeId);
            return null;
        }
        return journalEntryService.reverseEntry(
                jeId,
                "Reversion Pull+Diff AgroFusion: " + (reason != null ? reason : ""),
                "agrofusion-integration"
        ).getId();
    }

    private void voidLinkedInvoice(IntegrationTransfer t) {
        String externalId = t.getDocumentId();
        if (externalId == null) return;

        // Probar en AP invoices
        invoiceRepository.findByIntegrationSource_ExternalIdAndDeletedAtIsNull(externalId)
                .ifPresent(inv -> {
                    inv.setStatus(StatusesInvoices.VOIDED);
                    invoiceRepository.save(inv);
                    log.info("Invoice AP {} marcada VOIDED (Pull+Diff)", inv.getId());
                });

        // Probar en AR salesInvoice
        salesInvoiceRepository.findByIntegrationSource_ExternalIdAndDeletedAtIsNull(externalId)
                .ifPresent(si -> {
                    si.setStatus(com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceStatus.VOIDED);
                    salesInvoiceRepository.save(si);
                    log.info("SalesInvoice AR {} marcada VOIDED (Pull+Diff)", si.getId());
                });
    }

    // ============================================================
    // MODIFIED (HU-INT-RF-10 E2)
    // ============================================================

    private Map<String, Object> processModify(AgroFusionExchangeUpdateDTO dto) {
        if (dto.getDocument() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "changeType=MODIFIED requiere el campo 'Document' con el documento actualizado");
        }

        // 1. Reversar el original (implica marcar facturas VOIDED)
        Map<String, Object> cancelResult = processCancel(dto);

        // 2. Crear un batch "satelite" con el documento modificado y procesarlo
        Long newBatchId = createAndProcessSyntheticBatch(dto, "MODIFIED");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "MODIFIED");
        result.put("originalExchangeId", dto.getOriginalExchangeId());
        result.put("affectedEntries", cancelResult.get("affectedEntries"));
        result.put("newBatchId", newBatchId);
        return result;
    }

    // ============================================================
    // NEW (HU-INT-RF-10 E4)
    // ============================================================

    private Map<String, Object> processNew(AgroFusionExchangeUpdateDTO dto) {
        if (dto.getDocument() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "changeType=NEW requiere el campo 'Document' con el documento a agregar");
        }
        Long newBatchId = createAndProcessSyntheticBatch(dto, "NEW");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "NEW");
        result.put("originalExchangeId", dto.getOriginalExchangeId());
        result.put("newBatchId", newBatchId);
        return result;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private IntegrationBatch findOriginalBatch(String exchangeId) {
        return batchRepository
                .findByExchangeIdAndStandardVersionAndDeletedAtIsNull(exchangeId, STANDARD_VERSION)
                .orElseThrow(() -> new AaefMappingException(
                        AaefMappingException.ORIGINAL_NOT_FOUND,
                        "No se encontro lote original con exchangeId=" + exchangeId));
    }

    /**
     * Construye un batch sintetico con el documento nuevo/modificado y lo procesa
     * usando {@link AaefBatchProcessor}. Permite reusar toda la logica de mapeo
     * y generacion de JE sin duplicar codigo.
     */
    private Long createAndProcessSyntheticBatch(AgroFusionExchangeUpdateDTO dto, String tag) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ObjectNode metadata = objectMapper.createObjectNode();
            String syntheticExchangeId = dto.getOriginalExchangeId() + "-" + tag;
            metadata.put("ExchangeId", syntheticExchangeId);
            metadata.put("StandardVersion", STANDARD_VERSION);
            metadata.put("GeneratedAt", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            com.fasterxml.jackson.databind.node.ObjectNode srcSys = objectMapper.createObjectNode();
            srcSys.put("SystemId", "AgroFusion-PullDiff");
            srcSys.put("Version", "1.0");
            metadata.set("SourceSystem", srcSys);
            payload.set("metadata", metadata);

            com.fasterxml.jackson.databind.node.ObjectNode summary = objectMapper.createObjectNode();
            summary.put("TotalDocuments", 1);
            payload.set("summary", summary);

            // Distribuir el documento segun su tipo. Nota: el grupo "payroll" del
            // estandar AAEF original fue desestimado del alcance del proyecto.
            com.fasterxml.jackson.databind.node.ArrayNode invoices = objectMapper.createArrayNode();
            com.fasterxml.jackson.databind.node.ArrayNode transactions = objectMapper.createArrayNode();
            String docType = dto.getDocumentType() != null ? dto.getDocumentType().toUpperCase() : "INVOICE";
            switch (docType) {
                case "INVOICE": invoices.add(dto.getDocument()); break;
                case "TRANSACTION": transactions.add(dto.getDocument()); break;
                default:
                    throw new AaefMappingException(
                            AaefMappingException.UNSUPPORTED_TYPE,
                            "DocumentType no soportado: " + docType);
            }
            payload.set("invoices", invoices);
            payload.set("transactions", transactions);

            IntegrationBatch batch = IntegrationBatch.builder()
                    .exchangeId(syntheticExchangeId)
                    .standardVersion(STANDARD_VERSION)
                    .sourceSystemId("AgroFusion-PullDiff")
                    .sourceSystemName("AgroFusion Pull+Diff (HU-INT-RF-10)")
                    .receivedAt(LocalDateTime.now())
                    .totalDocuments(1)
                    .totalInvoices("INVOICE".equalsIgnoreCase(dto.getDocumentType()) ? 1 : 0)
                    .totalTransactions("TRANSACTION".equalsIgnoreCase(dto.getDocumentType()) ? 1 : 0)
                    .status(BatchStatus.RECEIVED)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            batch = batchRepository.save(batch);

            // Publicar evento para que BatchReceivedListener dispare el procesamiento
            // async DESPUES de que esta transaccion se haya comiteado (sino el batch
            // aun no es visible para el worker async).
            eventPublisher.publishEvent(new BatchReceivedEvent(this, batch.getId()));
            return batch.getId();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "Error serializando batch sintetico: " + e.getMessage());
        }
    }
}

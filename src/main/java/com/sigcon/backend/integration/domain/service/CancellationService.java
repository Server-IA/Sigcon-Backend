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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

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
     * Locks por document_id para serializar updates concurrentes al mismo doc.
     * Evita race condition: 2 updates simultaneos al mismo documento podrian
     * reversar ambos el mismo JE, dejando el segundo huerfano.
     */
    private final java.util.Map<String, ReentrantLock> docLocks = new ConcurrentHashMap<>();

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

        // RF-INT-14: serializar por document_id para evitar race condition
        // entre 2 updates concurrentes al mismo doc. Cada doc tiene su propio
        // lock; updates a docs distintos siguen procesandose en paralelo.
        String docKey = dto.getDocumentId() != null ? dto.getDocumentId() : "_NEW_" + System.nanoTime();
        ReentrantLock lock = docLocks.computeIfAbsent(docKey, k -> new ReentrantLock());
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
            // Cleanup: si nadie mas espera el lock, removerlo del map para evitar leak
            if (!lock.hasQueuedThreads()) {
                docLocks.remove(docKey, lock);
            }
        }
    }

    // ============================================================
    // CANCELLED (HU-INT-RF-10 E1)
    // ============================================================

    private Map<String, Object> processCancel(AgroFusionExchangeUpdateDTO dto) {
        // Spec RF-INT-14: cuando llega un Pull+Diff sobre un document_id, debemos
        // reversar el ASIENTO MÁS RECIENTE de ese documento (no recorrer todo el
        // lote padre). Pueden existir múltiples updates al mismo doc — siempre se
        // reversa el último.
        //
        // Antes: se buscaba por exchangeId del lote padre y se reversaban TODOS sus
        // transfers. Eso era incorrecto cuando un doc había sido actualizado N veces:
        // se terminaba reversando el JE original (que ya estaba REVERSED por el
        // update anterior) en vez del más reciente.

        // Validar que el lote padre exista (también valida ORIGINAL_NOT_FOUND)
        findOriginalBatch(dto.getOriginalExchangeId());

        if (dto.getDocumentId() == null || dto.getDocumentId().isBlank()) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "DocumentId es obligatorio para CANCELLED");
        }

        List<Long> reversedEntries = new java.util.ArrayList<>();

        // Buscar el ÚLTIMO transfer del documento (con asiento POSTED).
        IntegrationTransfer latest = transferRepository
                .findFirstByDocumentIdAndAccountingEntryIdIsNotNullAndDeletedAtIsNullOrderByProcessedAtDesc(
                        dto.getDocumentId())
                .orElse(null);

        if (latest == null) {
            log.warn("Pull+Diff CANCELLED: no se encontro transfer previo para document_id={}",
                    dto.getDocumentId());
        } else {
            Long jeId = latest.getAccountingEntryId();

            // Reversar JE (postear si esta en DRAFT)
            Long reversalId = reverseOrDelete(jeId, dto.getReason());
            reversedEntries.add(reversalId != null ? reversalId : jeId);

            // Marcar invoice/salesInvoice como VOIDED
            voidLinkedInvoice(latest);

            // Marcar transfer como reversado
            latest.setTransferStatus(TransferStatus.FAILED);
            latest.setErrorCode("CANCELLED_BY_AGROFUSION");
            latest.setErrorMessage("Cancelado via Pull+Diff: " + (dto.getReason() != null ? dto.getReason() : "sin motivo"));
            latest.setProcessedAt(LocalDateTime.now());
            transferRepository.save(latest);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "CANCELLED");
        result.put("originalExchangeId", dto.getOriginalExchangeId());
        result.put("documentId", dto.getDocumentId());
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
            // QA Bloque PA Bug 70 (HU-INT-13, 2026-05-09): GeneratedAt ahora es
            // fecha sola (yyyy-MM-dd) por compatibilidad con AAEF v1.1.
            metadata.put("GeneratedAt", java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
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
                    // Spec AAEF Bloque W: marcar como update y conservar
                    // OriginalExchangeId para que el AckClient elija el envelope
                    // PascalCase (AgroFusionAcknowledgment).
                    .isUpdate(true)
                    .originalExchangeId(dto.getOriginalExchangeId())
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

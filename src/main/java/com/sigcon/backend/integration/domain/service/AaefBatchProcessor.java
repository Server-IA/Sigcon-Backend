package com.sigcon.backend.integration.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.accounts_receivable.advances.application.CreateArAdvanceRequest;
import com.sigcon.backend.accounts_receivable.advances.domain.service.ArAdvanceService;
import com.sigcon.backend.accounts_receivable.payments.application.CreateArPaymentRequest;
import com.sigcon.backend.accounts_receivable.payments.domain.service.ArPaymentService;
import com.sigcon.backend.accounts_receivable.sales_invoices.application.CreateSalesInvoiceRequest;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.service.SalesInvoiceService;
import com.sigcon.backend.integration.application.AaefBatchRequest;
import com.sigcon.backend.integration.application.AaefInvoiceDTO;
import com.sigcon.backend.invoices.application.InvoiceFCRequestDTO;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.TypesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.invoices.domain.repository.TypeInvoiceRepository;
import com.sigcon.backend.invoices.domain.service.InvoiceService;
import com.sigcon.backend.integration.application.AaefTransactionDTO;
import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.IntegrationSource;
import com.sigcon.backend.integration.domain.model.IntegrationTransfer;
import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import com.sigcon.backend.integration.domain.model.enums.DocumentType;
import com.sigcon.backend.integration.domain.model.enums.SourceOrigin;
import com.sigcon.backend.integration.domain.model.enums.TransferStatus;
import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import com.sigcon.backend.integration.domain.repository.IntegrationTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * HU-INT-RF-04/05: Procesador asincrono de lotes AAEF.
 *
 * <p>Recoge un lote en estado {@code RECEIVED}, recorre cada documento
 * (invoice, transaction) y lo convierte a entidades SIGCON llamando
 * a los services existentes (SalesInvoiceService, ArPaymentService, etc.).
 *
 * <p>Por cada documento se crea un {@link IntegrationTransfer} que rastrea el
 * resultado (PROCESSED o FAILED + errorCode). Al terminar:
 * <ul>
 *   <li>Todos OK → status = {@code PROCESSED}</li>
 *   <li>Algunos OK y otros fallidos → status = {@code PARTIAL}</li>
 *   <li>Todos fallidos → status = {@code FAILED}</li>
 * </ul>
 * Luego dispara el envio del ACK a AgroFusion (en Paso posterior de Fase 2).
 *
 * <p>NOTA: el documento payroll del estandar AAEF original fue desestimado del
 * alcance del proyecto (borrador del grupo de documentacion, no es requerimiento
 * real). Solo se procesan invoices y transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AaefBatchProcessor {

    private final IntegrationBatchRepository batchRepository;
    private final IntegrationTransferRepository transferRepository;
    private final AaefInvoiceMapper invoiceMapper;
    private final AaefTransactionMapper transactionMapper;
    private final SalesInvoiceService salesInvoiceService;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final TypeInvoiceRepository typeInvoiceRepository;
    private final ArPaymentService arPaymentService;
    private final ArAdvanceService arAdvanceService;
    /** HU-AP-04 E5: payment AP via AAEF. */
    private final com.sigcon.backend.invoices.ap_payments.domain.service.ApPaymentService apPaymentService;
    private final ObjectMapper objectMapper;
    private final AgroFusionAckClient ackClient;
    /** HU-INT-RF-15 E4: registro append-only del resultado de cada transfer. */
    private final TransferHistoryService historyService;

    /** Codigo del tipo de factura "Factura de compra" en {@code types_invoices}. */
    private static final String PURCHASE_INVOICE_TYPE_CODE = "FC";

    /**
     * Procesa un lote en background. Se invoca desde el scheduler o directamente
     * despues de recibir el batch en {@link AaefReceiverService}.
     *
     * @param batchId id del {@link IntegrationBatch} a procesar
     */
    @Async
    public void processAsync(Long batchId) {
        log.info("Iniciando procesamiento async del lote {}", batchId);
        // Multi-tenant (Bloque G fix): el thread pool @Async no hereda el
        // TenantContext del request HTTP original (es un ThreadLocal). Leemos
        // el company_id del propio batch (columna no-nullable desde V10-A) y lo
        // establecemos manualmente para que los @Filter, @PrePersist y
        // AccountMappingService resuelvan la empresa correcta al crear
        // SalesInvoice / Invoices / JournalEntry derivados.
        Long companyId = batchRepository.findById(batchId)
                .map(b -> b.getCompanyId())
                .orElse(null);
        com.sigcon.backend.platform.tenant.TenantContext.setCompanyId(companyId);
        com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(false);
        try {
            processInternal(batchId);
        } catch (Exception e) {
            log.error("Error no controlado procesando lote {}", batchId, e);
            markBatchFailed(batchId, "Error no controlado: " + e.getMessage());
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.clear();
        }
    }

    /**
     * Procesa el lote dentro de una transaccion. Por cada documento se maneja el
     * error de forma aislada para permitir procesamiento parcial.
     */
    @Transactional
    public void processInternal(Long batchId) {
        IntegrationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Batch no encontrado: " + batchId));

        // Solo procesar batches en RECEIVED
        if (batch.getStatus() != BatchStatus.RECEIVED) {
            log.warn("Batch {} no esta en estado RECEIVED (actual: {}). Se omite.",
                    batchId, batch.getStatus());
            return;
        }

        batch.setStatus(BatchStatus.PROCESSING);
        batchRepository.save(batch);

        // Deserializar payload
        AaefBatchRequest payload;
        try {
            payload = objectMapper.readValue(batch.getPayloadJson(), AaefBatchRequest.class);
        } catch (JsonProcessingException e) {
            log.error("No se pudo deserializar payload del batch {}", batchId, e);
            markBatchFailed(batchId, "Error deserializando payload: " + e.getMessage());
            return;
        }

        List<IntegrationTransfer> transfers = new ArrayList<>();
        int processed = 0;
        int failed = 0;

        // Procesar invoices
        if (payload.getInvoices() != null) {
            for (JsonNode invoiceNode : payload.getInvoices()) {
                IntegrationTransfer t = processInvoiceDocument(batch, invoiceNode);
                transfers.add(t);
                if (t.getTransferStatus() == TransferStatus.PROCESSED) processed++;
                else failed++;
            }
        }

        // Procesar transactions
        if (payload.getTransactions() != null) {
            for (JsonNode txNode : payload.getTransactions()) {
                IntegrationTransfer t = processTransactionDocument(batch, txNode);
                transfers.add(t);
                if (t.getTransferStatus() == TransferStatus.PROCESSED) processed++;
                else failed++;
            }
        }

        // NOTA: el bloque "payroll" del estandar AAEF original era un borrador
        // del grupo de documentacion y fue desestimado del alcance del proyecto.
        // No se procesa aqui aunque venga en el payload.

        transferRepository.saveAll(transfers);

        // HU-INT-RF-15 E4: registrar el resultado de cada transfer en el historial.
        // Para retries, attemptNumber = retry_count del transfer (asignado por
        // TransferRetryService). Para procesamiento inicial siempre es 0.
        for (IntegrationTransfer t : transfers) {
            int attempt = t.getRetryCount() == null ? 0 : t.getRetryCount();
            String triggerSource = attempt == 0 ? "SYSTEM" : "MANUAL";
            String triggeredBy = attempt == 0 ? "system" : historyService.currentUsername();
            try {
                if (t.getTransferStatus() == TransferStatus.PROCESSED) {
                    historyService.recordSuccess(
                            t.getId(), attempt,
                            t.getAccountingEntryId(),
                            triggerSource, triggeredBy, null, null);
                } else {
                    historyService.recordFailure(
                            t.getId(), attempt,
                            t.getErrorCode(), t.getErrorMessage(),
                            triggerSource, triggeredBy, null, null);
                }
            } catch (Exception ex) {
                // El registro de historial NO debe romper el procesamiento del batch.
                log.warn("No se pudo registrar historial para transfer {}: {}",
                        t.getId(), ex.getMessage());
            }
        }

        // Determinar estado final del batch
        BatchStatus finalStatus;
        if (failed == 0 && processed > 0)       finalStatus = BatchStatus.PROCESSED;
        else if (processed == 0 && failed > 0)  finalStatus = BatchStatus.FAILED;
        else if (processed > 0)                 finalStatus = BatchStatus.PARTIAL;
        else                                    finalStatus = BatchStatus.PROCESSED; // lote vacio

        batch.setStatus(finalStatus);
        batch.setProcessedAt(LocalDateTime.now());
        batchRepository.save(batch);

        log.info("Lote {} procesado. processed={}, failed={}, status={}",
                batchId, processed, failed, finalStatus);

        // HU-INT-RF-07: enviar ACK al callback de AgroFusion
        ackClient.sendAckAsync(batchId);
    }

    // ======== Procesamiento de un invoice ========

    private IntegrationTransfer processInvoiceDocument(IntegrationBatch batch, JsonNode node) {
        String documentId = null;
        try {
            AaefInvoiceDTO invoice = objectMapper.treeToValue(node, AaefInvoiceDTO.class);
            documentId = invoice.getHeader() != null ? invoice.getHeader().getDocumentId() : null;

            // QA Bloque AX (HU-INT-13 tolerancia Type.Code, 2026-05-16):
            // Normalizar Type.Code ANTES de los is*() del dispatcher. Asi un
            // alias como "INVOICE" + Name="Factura de Venta" se mapea a "01"
            // y entra correctamente a la rama isSalesInvoice.
            invoiceMapper.validateTypeCode(invoice);

            if (invoiceMapper.isSalesInvoice(invoice)) {
                // Type=01 Venta
                CreateSalesInvoiceRequest req = invoiceMapper.toSalesInvoiceRequest(invoice);
                SalesInvoice created = salesInvoiceService.createSalesInvoice(req);

                // Marcar origen AAEF
                created.setIntegrationSource(IntegrationSource.builder()
                        .source(SourceOrigin.AAEF)
                        .externalId(documentId)
                        .exchangeId(batch.getExchangeId())
                        .build());
                salesInvoiceRepository.save(created);

                return successTransfer(batch, documentId, DocumentType.INVOICE,
                        created.getJournalEntryId());
            } else if (invoiceMapper.isPurchaseInvoice(invoice)) {
                // Type=02 Compra (HU-AP-01 E5 / HU-INT-RF-04 E2)
                InvoiceFCRequestDTO req = invoiceMapper.toPurchaseInvoiceRequest(invoice);
                TypesInvoices typeFC = typeInvoiceRepository
                        .findByCodeAndDeletedAtIsNull(PURCHASE_INVOICE_TYPE_CODE)
                        .orElseThrow(() -> new AaefMappingException(
                                AaefMappingException.MAPPING_ERROR,
                                "Tipo de factura 'FC' no encontrado en types_invoices"));

                AaefInvoiceDTO.Totals t = invoice.getTotals();
                java.math.BigDecimal subtotal = t.getSubtotal() != null ? t.getSubtotal() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal vat = t.getTotalVAT() != null ? t.getTotalVAT() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal withholdings = t.getTotalWithholdings() != null ? t.getTotalWithholdings() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalPayment = t.getTotalPayment() != null ? t.getTotalPayment() : java.math.BigDecimal.ZERO;

                Invoices created = invoiceService.createInvoiceFromAaef(
                        req, typeFC.getId(), subtotal, vat, withholdings, totalPayment);

                // Marcar origen AAEF
                created.setIntegrationSource(IntegrationSource.builder()
                        .source(SourceOrigin.AAEF)
                        .externalId(documentId)
                        .exchangeId(batch.getExchangeId())
                        .build());
                invoiceRepository.save(created);

                return successTransfer(batch, documentId, DocumentType.INVOICE,
                        created.getJournalEntryId());
            } else if (invoiceMapper.isFeesInvoice(invoice)) {
                // AAEF v1.1: Type=03 Honorarios -> AP con tratamiento tributario
                // especial (retencion en la fuente Art. 383/384 ET). Reusa el motor
                // de retenciones existente via tax rules del proveedor.
                InvoiceFCRequestDTO req = invoiceMapper.toPurchaseInvoiceRequest(invoice);
                TypesInvoices typeFC = typeInvoiceRepository
                        .findByCodeAndDeletedAtIsNull(PURCHASE_INVOICE_TYPE_CODE)
                        .orElseThrow(() -> new AaefMappingException(
                                AaefMappingException.MAPPING_ERROR,
                                "Tipo de factura 'FC' no encontrado en types_invoices"));
                AaefInvoiceDTO.Totals t = invoice.getTotals();
                java.math.BigDecimal subtotal = t.getSubtotal() != null ? t.getSubtotal() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal vat = t.getTotalVAT() != null ? t.getTotalVAT() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal withholdings = t.getTotalWithholdings() != null ? t.getTotalWithholdings() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal totalPayment = t.getTotalPayment() != null ? t.getTotalPayment() : java.math.BigDecimal.ZERO;
                Invoices created = invoiceService.createInvoiceFromAaef(
                        req, typeFC.getId(), subtotal, vat, withholdings, totalPayment);
                created.setIntegrationSource(IntegrationSource.builder()
                        .source(SourceOrigin.AAEF)
                        .externalId(documentId)
                        .exchangeId(batch.getExchangeId())
                        .build());
                // Marca el documento como honorarios en notes para distinguirlo en
                // reportes contables. Las retenciones aplicables se calculan via
                // los tax rules del proveedor (motor existente en InvoiceService).
                if (created.getNotes() == null || !created.getNotes().toUpperCase().contains("HONORARIO")) {
                    created.setNotes(("[HONORARIOS Type=03] " +
                            (created.getNotes() != null ? created.getNotes() : "")).trim());
                }
                invoiceRepository.save(created);
                return successTransfer(batch, documentId, DocumentType.INVOICE,
                        created.getJournalEntryId());
            } else if (invoiceMapper.isCreditNote(invoice) || invoiceMapper.isDebitNote(invoice)) {
                // AAEF v1.1: Type=04 NC / 05 ND -> requieren Pull+Diff
                // (mecanismo CancellationService) que vincula al documento original.
                String tipoNota = invoiceMapper.isCreditNote(invoice) ? "credito (Type=04)" : "debito (Type=05)";
                return failedTransfer(batch, documentId, DocumentType.INVOICE,
                        AaefMappingException.UNSUPPORTED_TYPE,
                        "Nota " + tipoNota + " debe enviarse via Pull+Diff "
                        + "(POST /api/contabilidad/aaef con AgroFusionExchangeUpdate envelope) "
                        + "para vincular al documento original", true);
            } else {
                // Type.Code invalido (ya capturado por validateTypeCode pero defense in depth)
                return failedTransfer(batch, documentId, DocumentType.INVOICE,
                        AaefMappingException.INVALID_TYPE_CODE,
                        "Header.Type.Code no es valido. Valores admitidos: 01,02,03,04,05", false);
            }
        } catch (AaefMappingException e) {
            return failedTransfer(batch, documentId, DocumentType.INVOICE,
                    e.getErrorCode(), e.getMessage(), e.isRetryAllowed());
        } catch (IllegalStateException e) {
            // HU-INT-RF-04 E7: periodo contable cerrado -> errorCode PERIOD_CLOSED
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("periodo") && (msg.contains("cerrado") || msg.contains("bloqueado"))) {
                return failedTransfer(batch, documentId, DocumentType.INVOICE,
                        AaefMappingException.PERIOD_CLOSED,
                        e.getMessage(), true);
            }
            log.error("Estado invalido mapeando invoice {}", documentId, e);
            return failedTransfer(batch, documentId, DocumentType.INVOICE,
                    AaefMappingException.MAPPING_ERROR,
                    "Estado invalido: " + e.getMessage(), true);
        } catch (Exception e) {
            log.error("Error mapeando invoice {}", documentId, e);
            return failedTransfer(batch, documentId, DocumentType.INVOICE,
                    AaefMappingException.MAPPING_ERROR,
                    "Error interno: " + e.getMessage(), true);
        }
    }

    // ======== Procesamiento de un transaction ========

    private IntegrationTransfer processTransactionDocument(IntegrationBatch batch, JsonNode node) {
        String documentId = null;
        try {
            AaefTransactionDTO tx = objectMapper.treeToValue(node, AaefTransactionDTO.class);
            documentId = tx.getDocumentId();
            transactionMapper.validate(tx);
            String typeCode = transactionMapper.getTypeCode(tx);

            switch (typeCode) {
                case AaefTransactionMapper.TYPE_PAY: {
                    AaefTransactionMapper.ResolvedInvoice resolved =
                            transactionMapper.resolveInvoiceByExternalId(tx.getRelatedInvoiceId());
                    if (resolved.getScope() == AaefTransactionMapper.InvoiceScope.AR) {
                        CreateArPaymentRequest req =
                                transactionMapper.toArPaymentRequest(tx, resolved.getId());
                        arPaymentService.registerPayment(req);
                        return successTransfer(batch, documentId, DocumentType.TRANSACTION, null);
                    } else {
                        // HU-AP-04 E5: pago AP via AAEF
                        com.sigcon.backend.invoices.ap_payments.application.CreateApPaymentRequest req =
                                transactionMapper.toApPaymentRequest(tx, resolved.getId());
                        apPaymentService.registerPayment(req);
                        return successTransfer(batch, documentId, DocumentType.TRANSACTION, null);
                    }
                }
                case AaefTransactionMapper.TYPE_ADV: {
                    CreateArAdvanceRequest req = transactionMapper.toArAdvanceRequest(tx);
                    arAdvanceService.registerAdvance(req);
                    return successTransfer(batch, documentId, DocumentType.TRANSACTION, null);
                }
                case AaefTransactionMapper.TYPE_REF:
                case AaefTransactionMapper.TYPE_ADJ: {
                    return failedTransfer(batch, documentId, DocumentType.TRANSACTION,
                            AaefMappingException.UNSUPPORTED_TYPE,
                            "Type=" + typeCode + " requiere Pull+Diff (Fase 4)", true);
                }
                default:
                    return failedTransfer(batch, documentId, DocumentType.TRANSACTION,
                            AaefMappingException.UNSUPPORTED_TYPE,
                            "Type desconocido: " + typeCode, false);
            }

        } catch (AaefMappingException e) {
            return failedTransfer(batch, documentId, DocumentType.TRANSACTION,
                    e.getErrorCode(), e.getMessage(), e.isRetryAllowed());
        } catch (Exception e) {
            log.error("Error mapeando transaction {}", documentId, e);
            return failedTransfer(batch, documentId, DocumentType.TRANSACTION,
                    AaefMappingException.MAPPING_ERROR,
                    "Error interno: " + e.getMessage(), true);
        }
    }

    // ======== Helpers ========

    private IntegrationTransfer successTransfer(IntegrationBatch batch, String documentId,
                                                 DocumentType type, Long accountingEntryId) {
        IntegrationTransfer t = IntegrationTransfer.builder()
                .batch(batch)
                .documentId(documentId != null ? documentId : "unknown")
                .documentType(type)
                .transferStatus(TransferStatus.PROCESSED)
                .accountingEntryId(accountingEntryId)
                .retryAllowed(true)
                .processedAt(LocalDateTime.now())
                .build();
        return t;
    }

    private IntegrationTransfer failedTransfer(IntegrationBatch batch, String documentId,
                                                DocumentType type, String errorCode,
                                                String errorMessage, boolean retryAllowed) {
        String truncatedMsg = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500) : errorMessage;
        return IntegrationTransfer.builder()
                .batch(batch)
                .documentId(documentId != null ? documentId : "unknown")
                .documentType(type)
                .transferStatus(TransferStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(truncatedMsg)
                .retryAllowed(retryAllowed)
                .processedAt(LocalDateTime.now())
                .build();
    }

    private void markBatchFailed(Long batchId, String errorMessage) {
        batchRepository.findById(batchId).ifPresent(b -> {
            b.setStatus(BatchStatus.FAILED);
            b.setErrorMessage(errorMessage != null && errorMessage.length() > 1000
                    ? errorMessage.substring(0, 1000) : errorMessage);
            b.setProcessedAt(LocalDateTime.now());
            batchRepository.save(b);
        });
    }
}

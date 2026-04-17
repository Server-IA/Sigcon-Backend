package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.integration.domain.service.BatchReceivedEvent;
import com.sigcon.backend.invoices.domain.events.*;
import com.sigcon.backend.third_parties.third_parties.domain.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * HU-AU-01/03: Listener centralizado que escucha TODOS los eventos Spring
 * del sistema y los registra en el log de auditoria inmutable.
 *
 * <p>Cada handler usa {@code @TransactionalEventListener(AFTER_COMMIT)} para
 * que el registro de auditoria se cree DESPUES de que la operacion original
 * se haya comprometido exitosamente.
 *
 * <p>Escucha los 8 eventos existentes (TER, AP, INT). Nuevos eventos de otros
 * modulos pueden agregarse con un metodo adicional aqui sin tocar los servicios
 * originales.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogService auditLogService;

    // ─── Terceros (TER) ─────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThirdPartyCreated(ThirdPartyCreatedEvent event) {
        auditLogService.register(AuditAction.CREATE, AuditModule.TER, AuditSeverity.MEDIUM,
                "ThirdParty", event.getThirdPartyId(),
                "Tercero creado: " + event.getBusinessName(),
                null, null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThirdPartyUpdated(ThirdPartyUpdatedEvent event) {
        String changes = event.getChangedFields() != null ? event.getChangedFields().toString() : null;
        auditLogService.register(AuditAction.UPDATE, AuditModule.TER, AuditSeverity.MEDIUM,
                "ThirdParty", event.getThirdPartyId(),
                "Tercero actualizado: " + event.getBusinessName(),
                changes, null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThirdPartyDeleted(ThirdPartyDeletedEvent event) {
        auditLogService.register(AuditAction.DELETE, AuditModule.TER, AuditSeverity.HIGH,
                "ThirdParty", event.getThirdPartyId(),
                "Tercero eliminado: " + event.getBusinessName()
                        + " | Justificacion: " + event.getJustification(),
                null, null, null);
    }

    // ─── Cuentas por Pagar (AP) ─────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApInvoiceCreated(ApInvoiceCreatedEvent event) {
        auditLogService.register(AuditAction.CREATE, AuditModule.AP, AuditSeverity.MEDIUM,
                "ApInvoice", event.getInvoiceId(),
                "Factura de compra creada por $" + event.getAmount(),
                null, null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApInvoiceUpdated(ApInvoiceUpdatedEvent event) {
        auditLogService.register(AuditAction.UPDATE, AuditModule.AP, AuditSeverity.MEDIUM,
                "ApInvoice", event.getInvoiceId(),
                "Factura de compra actualizada",
                null, null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApInvoiceDeleted(ApInvoiceDeletedEvent event) {
        auditLogService.register(AuditAction.DELETE, AuditModule.AP, AuditSeverity.HIGH,
                "ApInvoice", event.getInvoiceId(),
                "Factura de compra eliminada",
                null, null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApPaymentProcessed(ApPaymentProcessedEvent event) {
        auditLogService.register(AuditAction.CREATE, AuditModule.AP, AuditSeverity.MEDIUM,
                "ApPayment", event.getPaymentId(),
                "Pago procesado por $" + event.getAmount() + " a factura #" + event.getInvoiceId(),
                null, null, null);
    }

    // ─── Integracion AAEF (INT) ─────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBatchReceived(BatchReceivedEvent event) {
        log.info("[AuditEventListener] BatchReceivedEvent batch={}", event.getBatchId());
        try {
            auditLogService.register(AuditAction.CREATE, AuditModule.INT, AuditSeverity.MEDIUM,
                    "IntegrationBatch", event.getBatchId(),
                    "Lote AAEF recibido para procesamiento",
                    null, null, null);
        } catch (Exception e) {
            log.error("[AuditEventListener] Error en onBatchReceived batch={}", event.getBatchId(), e);
        }
    }
}

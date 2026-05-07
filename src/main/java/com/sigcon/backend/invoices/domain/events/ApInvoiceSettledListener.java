package com.sigcon.backend.invoices.domain.events;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.parametrization.notifications.application.PublishEventRequest;
import com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity;
import com.sigcon.backend.parametrization.notifications.domain.service.NotificationService;
import com.sigcon.backend.platform.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * QA Bloque AU+ HU-AP-03 E1 (2026-05-06): listener AFTER_COMMIT que registra
 * en la bitacora de Contabilidad General el cierre formal de una factura AP.
 *
 * <p>La HU exige que cuando una factura se liquida, ademas de la auditoria del
 * modulo AP, CG vea reflejada la deuda saldada. Este listener cierra ese gap
 * generando una entrada de audit con modulo CG (visible en /auditoria/logs
 * filtrando por modulo CG) que documenta el cambio de saldo del proveedor.</p>
 *
 * <p>NO genera asiento contable porque los pagos individuales (que llevaron a
 * settle) ya hicieron los movimientos D CxP / C Bancos. El balance contable
 * del proveedor ya esta en cero. La notificacion CG es informativa para
 * trazabilidad, no para alterar el libro mayor.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApInvoiceSettledListener {

    private final AuditPublisher auditPublisher;
    private final InvoiceRepository invoiceRepository;

    @Autowired(required = false)
    private NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApInvoiceSettled(ApInvoiceSettledEvent event) {
        try {
            String description = "Deuda saldada con proveedor "
                    + (event.getThirdPartyName() != null ? event.getThirdPartyName() : "?")
                    + " | Factura " + event.getResolutionInvoice()
                    + " | Total " + (event.getTotalAmount() != null ? event.getTotalAmount() : "0")
                    + " | CxP cerrada (todos los pagos conciliados)";
            auditPublisher.publishUpdate(AuditModule.CG, "AccountPayable", event.getInvoiceId(), description);
            log.info("HU-AP-03 E1: cierre CxP registrado en CG para factura {}", event.getInvoiceId());
        } catch (RuntimeException ex) {
            log.warn("HU-AP-03 E1 listener: no se pudo registrar cierre en CG para factura {}: {}",
                    event.getInvoiceId(), ex.getMessage());
        }

        // QA Bloque AU+ HU-AP-03 E1 (2026-05-07): publicar notificacion in-app
        // a los roles suscritos a AP_INVOICE_SETTLED en el modulo CG. El QA
        // reporto que el audit_log con modulo=CG no era visible para el
        // contador en su flujo normal, asi que ahora ademas mandamos una
        // notificacion en /notifications visible para CG.
        if (notificationService == null) return;
        try {
            Invoices inv = invoiceRepository.findById(event.getInvoiceId()).orElse(null);
            if (inv == null || inv.getCompanyId() == null) return;
            // El listener AFTER_COMMIT corre fuera del request original, asi que
            // hidratamos el TenantContext con la empresa de la factura para que
            // NotificationService.publishByRoleSubscription pueda resolver
            // suscriptores tenant-scoped sin leakear cross-empresa.
            TenantContext.runAs(inv.getCompanyId(), false, () -> {
                PublishEventRequest req = PublishEventRequest.builder()
                        .eventKey("AP_INVOICE_SETTLED")
                        .companyId(inv.getCompanyId())
                        .sourceId(event.getInvoiceId())
                        .sourceType("Invoice")
                        .severity(Severity.INFO)
                        .title("Factura de compra liquidada")
                        .body("Factura " + event.getResolutionInvoice()
                                + " del proveedor " + (event.getThirdPartyName() != null ? event.getThirdPartyName() : "?")
                                + " fue LIQUIDADA. Deuda saldada por $" + (event.getTotalAmount() != null ? event.getTotalAmount() : "0")
                                + ". Verifique que la CxP refleje saldo cero.")
                        .actionUrl("/accounts-payable/invoices?id=" + event.getInvoiceId())
                        .build();
                notificationService.publishByRoleSubscription(req);
            });
            log.info("HU-AP-03 E1: notificacion CG publicada para factura {}", event.getInvoiceId());
        } catch (RuntimeException ex) {
            log.warn("HU-AP-03 E1: no se pudo publicar notificacion CG para factura {}: {}",
                    event.getInvoiceId(), ex.getMessage());
        }
    }
}

package com.sigcon.backend.invoices.domain.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

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
    }
}

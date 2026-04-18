package com.sigcon.backend.invoices.domain.events;

import org.springframework.context.ApplicationEvent;

/**
 * Evento de dominio publicado cuando una factura de compra (AP) es eliminada (soft delete).
 *
 * <p>Permite a otros modulos reaccionar a eliminaciones, por ejemplo para anular
 * JE asociados o limpiar referencias. Publicado por {@code InvoiceService.delete()}.</p>
 *
 * @see ApInvoiceCreatedEvent
 * @see ApInvoiceUpdatedEvent
 */
public class ApInvoiceDeletedEvent extends ApplicationEvent {

    private final Long invoiceId;
    private final Long thirdPartyId;

    public ApInvoiceDeletedEvent(Object source, Long invoiceId, Long thirdPartyId) {
        super(source);
        this.invoiceId = invoiceId;
        this.thirdPartyId = thirdPartyId;
    }

    public Long getInvoiceId() { return invoiceId; }
    public Long getThirdPartyId() { return thirdPartyId; }
}

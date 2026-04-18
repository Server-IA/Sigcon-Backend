package com.sigcon.backend.invoices.domain.events;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEvent;

/**
 * Evento de dominio publicado cuando una factura de compra (AP) es actualizada.
 *
 * <p>Permite a otros modulos (CG, BNK, ACT) reaccionar a modificaciones de facturas
 * sin acoplamiento directo. Publicado por {@code InvoiceService.update()} al
 * finalizar la actualizacion.</p>
 *
 * @see ApInvoiceCreatedEvent
 * @see ApInvoiceDeletedEvent
 */
public class ApInvoiceUpdatedEvent extends ApplicationEvent {

    private final Long invoiceId;
    private final BigDecimal amount;
    private final Long thirdPartyId;

    public ApInvoiceUpdatedEvent(Object source, Long invoiceId, BigDecimal amount, Long thirdPartyId) {
        super(source);
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.thirdPartyId = thirdPartyId;
    }

    public Long getInvoiceId() { return invoiceId; }
    public BigDecimal getAmount() { return amount; }
    public Long getThirdPartyId() { return thirdPartyId; }
}

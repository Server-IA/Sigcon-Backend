package com.sigcon.backend.invoices.domain.events;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEvent;

/**
 * QA Bloque AU+ HU-AP-03 E1 (2026-05-06): evento dedicado al cierre formal de
 * una factura de compra. Antes solo se publicaba {@link ApInvoiceUpdatedEvent}
 * generico al hacer settle, lo que dejaba el modulo CG sin notificacion
 * explicita de la deuda saldada. La HU exige que CG vea reflejado el cierre
 * de la obligacion con su propia entrada de auditoria/log.
 *
 * <p>Consumido por {@code ApInvoiceSettledListener} en CG.</p>
 */
public class ApInvoiceSettledEvent extends ApplicationEvent {

    private final Long invoiceId;
    private final String resolutionInvoice;
    private final String thirdPartyName;
    private final Long thirdPartyId;
    private final BigDecimal totalAmount;

    public ApInvoiceSettledEvent(Object source, Long invoiceId, String resolutionInvoice,
                                  String thirdPartyName, Long thirdPartyId, BigDecimal totalAmount) {
        super(source);
        this.invoiceId = invoiceId;
        this.resolutionInvoice = resolutionInvoice;
        this.thirdPartyName = thirdPartyName;
        this.thirdPartyId = thirdPartyId;
        this.totalAmount = totalAmount;
    }

    public Long getInvoiceId() { return invoiceId; }
    public String getResolutionInvoice() { return resolutionInvoice; }
    public String getThirdPartyName() { return thirdPartyName; }
    public Long getThirdPartyId() { return thirdPartyId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}

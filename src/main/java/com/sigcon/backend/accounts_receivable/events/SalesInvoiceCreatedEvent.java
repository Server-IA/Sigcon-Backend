package com.sigcon.backend.accounts_receivable.events;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEvent;

/**
 * HU-AR-01A E4: Evento publicado al crear una factura de venta tras
 * persistencia exitosa y generacion del JE.
 *
 * <p>Permite a otros modulos (CG, INT, AU) reaccionar sin acoplamiento
 * directo. Replica el patron de {@code ApInvoiceCreatedEvent} de AP.</p>
 */
public class SalesInvoiceCreatedEvent extends ApplicationEvent {

    private final Long salesInvoiceId;
    private final String invoiceNumber;
    private final Long thirdPartyId;
    private final BigDecimal totalAmount;
    private final Long journalEntryId;
    private final String source;

    public SalesInvoiceCreatedEvent(Object source, Long salesInvoiceId, String invoiceNumber,
                                    Long thirdPartyId, BigDecimal totalAmount,
                                    Long journalEntryId, String origin) {
        super(source);
        this.salesInvoiceId = salesInvoiceId;
        this.invoiceNumber = invoiceNumber;
        this.thirdPartyId = thirdPartyId;
        this.totalAmount = totalAmount;
        this.journalEntryId = journalEntryId;
        this.source = origin;
    }

    public Long getSalesInvoiceId() { return salesInvoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public Long getThirdPartyId() { return thirdPartyId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Long getJournalEntryId() { return journalEntryId; }
    public String getSource() { return source; }
}

package com.sigcon.backend.accounts_receivable.events;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEvent;

/**
 * HU-AR-02 E4: Evento publicado al registrar un cobro/abono sobre una factura
 * de venta tras persistencia exitosa y generacion del JE.
 */
public class ArPaymentProcessedEvent extends ApplicationEvent {

    private final Long paymentId;
    private final Long salesInvoiceId;
    private final BigDecimal amount;
    private final Long journalEntryId;
    private final String paymentMethod;
    private final boolean partial;

    public ArPaymentProcessedEvent(Object source, Long paymentId, Long salesInvoiceId,
                                   BigDecimal amount, Long journalEntryId,
                                   String paymentMethod, boolean partial) {
        super(source);
        this.paymentId = paymentId;
        this.salesInvoiceId = salesInvoiceId;
        this.amount = amount;
        this.journalEntryId = journalEntryId;
        this.paymentMethod = paymentMethod;
        this.partial = partial;
    }

    public Long getPaymentId() { return paymentId; }
    public Long getSalesInvoiceId() { return salesInvoiceId; }
    public BigDecimal getAmount() { return amount; }
    public Long getJournalEntryId() { return journalEntryId; }
    public String getPaymentMethod() { return paymentMethod; }
    public boolean isPartial() { return partial; }
}

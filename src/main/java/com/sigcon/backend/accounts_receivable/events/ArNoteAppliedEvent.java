package com.sigcon.backend.accounts_receivable.events;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEvent;

/**
 * HU-AR-07 E3: Evento publicado al aplicar una nota credito/debito
 * sobre una factura de venta tras persistencia exitosa y generacion del JE.
 */
public class ArNoteAppliedEvent extends ApplicationEvent {

    private final Long noteId;
    private final Long salesInvoiceId;
    private final String noteType; // CREDIT | DEBIT
    private final BigDecimal amount;
    private final Long journalEntryId;
    private final String reason;

    public ArNoteAppliedEvent(Object source, Long noteId, Long salesInvoiceId,
                              String noteType, BigDecimal amount,
                              Long journalEntryId, String reason) {
        super(source);
        this.noteId = noteId;
        this.salesInvoiceId = salesInvoiceId;
        this.noteType = noteType;
        this.amount = amount;
        this.journalEntryId = journalEntryId;
        this.reason = reason;
    }

    public Long getNoteId() { return noteId; }
    public Long getSalesInvoiceId() { return salesInvoiceId; }
    public String getNoteType() { return noteType; }
    public BigDecimal getAmount() { return amount; }
    public Long getJournalEntryId() { return journalEntryId; }
    public String getReason() { return reason; }
}

package com.sigcon.backend.invoices.domain.events;

import java.time.LocalDate;
import org.springframework.context.ApplicationEvent;

/**
 * HU-AP-13 E2 (Bloque AS): se dispara cuando se edita una factura cuya factura
 * de compra tiene un asiento contable en estado POSTED, y los cambios afectan
 * datos contables (fecha o resolucion). Un listener AFTER_COMMIT crea el
 * asiento de ajuste DRAFT vinculado via correctionOf.
 */
public class ApInvoicePostedEditedEvent extends ApplicationEvent {

    private final Long invoiceId;
    private final Long originalJournalEntryId;
    private final String resolutionInvoice;
    private final LocalDate newInvoiceDate;

    public ApInvoicePostedEditedEvent(Object source, Long invoiceId,
                                       Long originalJournalEntryId,
                                       String resolutionInvoice,
                                       LocalDate newInvoiceDate) {
        super(source);
        this.invoiceId = invoiceId;
        this.originalJournalEntryId = originalJournalEntryId;
        this.resolutionInvoice = resolutionInvoice;
        this.newInvoiceDate = newInvoiceDate;
    }

    public Long getInvoiceId() { return invoiceId; }
    public Long getOriginalJournalEntryId() { return originalJournalEntryId; }
    public String getResolutionInvoice() { return resolutionInvoice; }
    public LocalDate getNewInvoiceDate() { return newInvoiceDate; }
}

package com.sigcon.backend.invoices.ap_notes.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para notas credito y debito de facturas de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApNoteDTO {

    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private String noteType;
    private String noteNumber;
    private BigDecimal amount;
    private String reason;

    /**
     * QA Bloque BK (HU-INT-RF-05 E5, 2026-05-18): exponer el journalEntryId
     * generado automaticamente al crear la nota. El AaefBatchProcessor lo
     * extrae via reflection para poblar {@code af_accounting_transfers.accounting_entry_id}
     * y dejar trazabilidad completa del REF/ADJ AAEF.
     */
    private Long journalEntryId;
}

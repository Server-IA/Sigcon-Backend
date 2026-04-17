package com.sigcon.backend.accounts_receivable.credit_debit_notes.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para notas credito y debito de facturas de venta.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArNoteDTO {

    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private String noteType;
    private String noteNumber;
    private BigDecimal amount;
    private String reason;
    private Long journalEntryId;
}

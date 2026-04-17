package com.sigcon.backend.accounts_receivable.payments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para cobros y abonos de facturas de venta.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArPaymentDTO {

    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentReference;
    private String paymentMethod;
    private Long bankAccountId;
    private Long cashId;
    private Long bankMovementId;
    private String status;
    private Long journalEntryId;
    private String notes;
}

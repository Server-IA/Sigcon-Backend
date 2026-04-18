package com.sigcon.backend.invoices.ap_payments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para pagos y abonos de facturas de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApPaymentDTO {

    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentReference;
    private String paymentMethod;
    private String status;
    private String notes;
}

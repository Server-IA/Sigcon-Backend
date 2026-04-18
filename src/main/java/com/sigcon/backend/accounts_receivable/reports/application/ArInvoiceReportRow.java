package com.sigcon.backend.accounts_receivable.reports.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila de reporte de factura de venta utilizada en reportes de Cuentas por Cobrar.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArInvoiceReportRow {

    private Long invoiceId;
    private String invoiceNumber;
    private Long thirdPartyId;
    private String thirdPartyNit;
    private String thirdPartyName;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal balanceDue;
    private Long daysOverdue;
}

package com.sigcon.backend.accounts_receivable.reports.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AR-05: Resumen por cliente. Incluye totales y detalle opcional de facturas.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArCustomerSummaryDTO {

    private Long thirdPartyId;
    private String thirdPartyNit;
    private String thirdPartyName;
    private Integer invoiceCount;
    private BigDecimal totalInvoiced;
    private BigDecimal totalPending;
    private List<ArInvoiceReportRow> invoices;
}

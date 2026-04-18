package com.sigcon.backend.accounts_receivable.reports.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AR-12: Saldo pendiente total de un cliente con detalle de facturas abiertas.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArCustomerBalanceDTO {

    private Long thirdPartyId;
    private String thirdPartyNit;
    private String thirdPartyName;
    private BigDecimal totalPending;
    private Integer openInvoiceCount;
    private List<ArInvoiceReportRow> openInvoices;
}

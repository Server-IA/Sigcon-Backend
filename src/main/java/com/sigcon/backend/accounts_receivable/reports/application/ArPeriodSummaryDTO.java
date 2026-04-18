package com.sigcon.backend.accounts_receivable.reports.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AR-05: Resumen por periodo (totales facturado, cobrado y pendiente).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArPeriodSummaryDTO {

    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalInvoiced;
    private BigDecimal totalCollected;
    private BigDecimal totalPending;
    private Integer invoiceCount;
}

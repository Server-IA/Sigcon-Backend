package com.sigcon.backend.accounts_receivable.reports.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AR-12: Estado de cuenta de cliente para un rango de fechas.
 * Incluye facturas, cobros, notas credito/debito y anticipos aplicados.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArCustomerStatementDTO {

    private Long thirdPartyId;
    private String thirdPartyNit;
    private String thirdPartyName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalInvoiced;
    private BigDecimal totalCollected;
    private BigDecimal totalCreditNotes;
    private BigDecimal totalDebitNotes;
    private BigDecimal totalAdvances;
    private BigDecimal balance;
    private List<StatementLine> movements;

    /** Movimiento individual dentro del estado de cuenta. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatementLine {
        private LocalDate date;
        private String type;
        private String reference;
        private BigDecimal debit;
        private BigDecimal credit;
        private BigDecimal runningBalance;
    }
}

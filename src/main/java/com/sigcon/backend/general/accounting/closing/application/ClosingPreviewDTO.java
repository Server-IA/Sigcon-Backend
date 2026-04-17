package com.sigcon.backend.general.accounting.closing.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de previsualizacion de cierre contable.
 * Muestra las lineas que se generarian en el asiento de cierre
 * sin ejecutar la operacion.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClosingPreviewDTO {

    private Integer fiscalYear;
    private Integer fiscalMonth;
    private String closingType;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal netResult;
    private String netResultLabel;
    private List<ClosingLinePreviewDTO> lines;

    /**
     * Linea de previsualizacion del asiento de cierre.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ClosingLinePreviewDTO {
        private Long accountingAccountId;
        private String accountCode;
        private String accountName;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String description;
    }
}

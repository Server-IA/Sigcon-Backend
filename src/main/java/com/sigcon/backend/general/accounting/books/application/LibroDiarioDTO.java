package com.sigcon.backend.general.accounting.books.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para una entrada del Libro Diario.
 * Contiene la cabecera del asiento y sus lineas de detalle.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LibroDiarioDTO {

    /** Identificador del asiento contable. */
    private Long entryId;

    /** Numero consecutivo del asiento dentro del anio fiscal. */
    private Long entryNumber;

    /** Fecha del asiento. */
    private LocalDate date;

    /** Descripcion o glosa del asiento. */
    private String description;

    /** Estado del asiento (POSTED). */
    private String status;

    /** Total debitos del asiento. */
    private BigDecimal totalDebit;

    /** Total creditos del asiento. */
    private BigDecimal totalCredit;

    /** Lineas de detalle del asiento. */
    private List<LibroDiarioLineDTO> lines;

    /**
     * DTO para cada linea de detalle dentro del Libro Diario.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LibroDiarioLineDTO {
        private Long lineId;
        private Integer lineOrder;
        private Long accountingAccountId;
        private String accountCode;
        private String accountName;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String description;
        private String thirdPartyNit;
        private String costCenterName;
    }
}

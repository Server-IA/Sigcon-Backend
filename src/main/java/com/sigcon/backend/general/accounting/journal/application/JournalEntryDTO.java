package com.sigcon.backend.general.accounting.journal.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para la cabecera de un asiento contable.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JournalEntryDTO {

    private Long id;
    private Long entryNumber;
    /**
     * Codigo del comprobante con prefijo segun su rol contable.
     * - JE-{anio}-{numero}  : asiento normal
     * - REV-{anio}-{numero} : asiento de reversion (HU-CG-08B E3)
     * - COR-{anio}-{numero} : asiento de correccion (HU-CG-07B)
     * Se calcula en JournalEntryService.toDTO segun reversalOf/correctionOf.
     */
    private String voucherCode;
    private Integer fiscalYear;
    private LocalDate entryDate;
    private Integer periodYear;
    private Integer periodMonth;
    private String description;
    private String sourceModule;
    private Long sourceId;
    private String status;
    private Long reversalOfId;
    /** Numero del asiento original que esta siendo reversado (para mostrar en UI). */
    private Long reversalOfNumber;
    /** voucherCode del asiento original reversado (e.g. JE-2026-1). */
    private String reversalOfVoucherCode;
    private Long correctionOfId;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private List<JournalEntryLineDTO> lines;
    private String createdBy;
    private LocalDateTime createdAt;
}

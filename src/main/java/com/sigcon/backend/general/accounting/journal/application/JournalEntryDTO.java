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
    private Integer fiscalYear;
    private LocalDate entryDate;
    private Integer periodYear;
    private Integer periodMonth;
    private String description;
    private String sourceModule;
    private Long sourceId;
    private String status;
    private Long reversalOfId;
    private Long correctionOfId;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private List<JournalEntryLineDTO> lines;
    private String createdBy;
    private LocalDateTime createdAt;
}

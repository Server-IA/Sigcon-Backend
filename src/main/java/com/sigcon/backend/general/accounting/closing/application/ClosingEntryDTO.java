package com.sigcon.backend.general.accounting.closing.application;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para un registro de cierre contable.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClosingEntryDTO {

    private Long id;
    private Integer fiscalYear;
    private Integer fiscalMonth;
    private String closingType;
    private Long journalEntryId;
    private String status;
    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
}

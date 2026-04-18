package com.sigcon.backend.accounts_receivable.advances.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para anticipos de clientes.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArAdvanceDTO {

    private Long id;
    private Long thirdPartyId;
    private String thirdPartyName;
    private BigDecimal amount;
    private BigDecimal appliedAmount;
    private BigDecimal availableAmount;
    private LocalDate advanceDate;
    private String advanceReference;
    private String status;
    private Long bankMovementId;
    private Long bankAccountId;
    private Long cashId;
    private Long journalEntryId;
    private String notes;
}

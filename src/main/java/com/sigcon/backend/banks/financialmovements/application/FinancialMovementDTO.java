package com.sigcon.backend.banks.financialmovements.application;

import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialMovementDTO {
    private Long id;
    private Long bankAccountId;
    private LocalDate movementDate;
    private BigDecimal amount;
    private String description;
    private String externalReference;
    private FinancialMovementSourceType sourceType;
    private String flowActivity;
    private Long matchedCheckId;
    private Long matchedVoucherId;
    /** QA-BLOQUE-AP v2 (2026-04-30): expuesto al frontend para que la columna
     * EMPAREJADO muestre el JE asociado tras hacer match. */
    private Long matchedJournalEntryId;
    /** Etiqueta legible (JE-2026-N) para mostrar en UI. */
    private String matchedJournalEntryNumber;
    private Long reconciliationSessionId;
}

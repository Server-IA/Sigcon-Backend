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
    private Long matchedCheckId;
    private Long matchedVoucherId;
    private Long reconciliationSessionId;
}

package com.sigcon.backend.banks.reconciliation.application;

import com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliationSessionDTO {
    private Long id;
    private Long bankAccountId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal statementOpeningBalance;
    private BigDecimal statementClosingBalance;
    private ReconciliationSessionStatus status;
    private String notes;
    private LocalDateTime closedAt;
    private Long closedBy;
    private LocalDateTime createdAt;
}

package com.sigcon.backend.banks.reconciliation.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBankReconciliationStatementBalancesRequest {

    private BigDecimal statementOpeningBalance;
    private BigDecimal statementClosingBalance;
}

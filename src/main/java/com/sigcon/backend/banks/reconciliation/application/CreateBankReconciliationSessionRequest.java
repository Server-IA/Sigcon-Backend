package com.sigcon.backend.banks.reconciliation.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateBankReconciliationSessionRequest {

    @NotNull(message = "La fecha inicial del periodo es obligatoria")
    private LocalDate periodStart;

    @NotNull(message = "La fecha final del periodo es obligatoria")
    private LocalDate periodEnd;

    private BigDecimal statementOpeningBalance;
    private BigDecimal statementClosingBalance;

    @Size(max = 500)
    private String notes;
}

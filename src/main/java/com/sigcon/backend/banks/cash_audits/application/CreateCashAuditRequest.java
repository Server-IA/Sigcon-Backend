package com.sigcon.backend.banks.cash_audits.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para registrar un nuevo arqueo de caja.
 * El saldo del sistema se calcula automaticamente desde la caja.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCashAuditRequest {

    @NotNull(message = "El ID de la caja es obligatorio.")
    private Long cashId;

    @NotNull(message = "La fecha del arqueo es obligatoria.")
    private LocalDate auditDate;

    @NotNull(message = "El saldo fisico contado es obligatorio.")
    private BigDecimal physicalBalance;

    private String notes;
}

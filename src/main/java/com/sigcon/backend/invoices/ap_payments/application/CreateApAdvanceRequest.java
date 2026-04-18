package com.sigcon.backend.invoices.ap_payments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para registrar un anticipo a un proveedor.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateApAdvanceRequest {

    /** ID del tercero (proveedor) beneficiario. */
    @NotNull(message = "El ID del tercero es obligatorio")
    private Long thirdPartyId;

    /** Monto del anticipo. */
    @NotNull(message = "El monto del anticipo es obligatorio")
    private BigDecimal amount;

    /** Fecha del anticipo. */
    @NotNull(message = "La fecha del anticipo es obligatoria")
    private LocalDate advanceDate;

    /** ID de la cuenta bancaria origen (si aplica). */
    private Long bankAccountId;

    /** ID de la caja origen (si aplica). */
    private Long cashId;

    /** Observaciones adicionales. */
    private String notes;
}

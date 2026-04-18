package com.sigcon.backend.accounts_receivable.advances.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para registrar un anticipo recibido de un cliente.
 * Cubre HU AR-09.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateArAdvanceRequest {

    /** ID del tercero (cliente) emisor del anticipo. */
    @NotNull(message = "El ID del tercero es obligatorio")
    private Long thirdPartyId;

    /** Monto del anticipo. */
    @NotNull(message = "El monto del anticipo es obligatorio")
    private BigDecimal amount;

    /** Fecha del anticipo. */
    @NotNull(message = "La fecha del anticipo es obligatoria")
    private LocalDate advanceDate;

    /** Referencia del anticipo. */
    private String advanceReference;

    /** ID del movimiento bancario origen. */
    private Long bankMovementId;

    /** ID de la cuenta bancaria destino (si aplica). */
    private Long bankAccountId;

    /** ID de la caja destino (si aplica). */
    private Long cashId;

    /** Observaciones adicionales. */
    private String notes;
}

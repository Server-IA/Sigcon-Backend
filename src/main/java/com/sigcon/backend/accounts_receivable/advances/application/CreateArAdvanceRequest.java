package com.sigcon.backend.accounts_receivable.advances.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    // QA CXC Bug 3 (2026-06-03 / IEEE AR-RF-09): la referencia admite maximo
    // 100 caracteres.
    /** Referencia del anticipo (opcional, maximo 100 caracteres). */
    @Size(max = 100, message = "La referencia no puede superar los 100 caracteres")
    private String advanceReference;

    /** ID del movimiento bancario origen. */
    private Long bankMovementId;

    /** ID de la cuenta bancaria destino (si aplica). */
    private Long bankAccountId;

    /** ID de la caja destino (si aplica). */
    private Long cashId;

    // QA CXC Bug 3 (2026-06-03 / IEEE AR-RF-09): las notas admiten maximo 500.
    /** Observaciones adicionales (opcional, maximo 500 caracteres). */
    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;
}

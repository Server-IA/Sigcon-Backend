package com.sigcon.backend.banks.financialmovements.application;

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
public class CreateBankFinancialMovementRequest {

    @NotNull(message = "La fecha del movimiento es obligatoria")
    private LocalDate movementDate;

    @NotNull(message = "El importe es obligatorio")
    private BigDecimal amount;

    @Size(max = 500)
    private String description;

    @Size(max = 100)
    private String externalReference;

    /** Opcional: asociar el movimiento a una sesión de conciliación abierta */
    private Long reconciliationSessionId;
}

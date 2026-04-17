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

    /** Clasificacion NIC 7: OPERATIVA, INVERSION, FINANCIACION */
    @Size(max = 20)
    private String flowActivity;

    /** Opcional: asociar el movimiento a una sesion de conciliacion abierta */
    private Long reconciliationSessionId;
}

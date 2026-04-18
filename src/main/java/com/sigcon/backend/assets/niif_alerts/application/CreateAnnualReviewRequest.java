package com.sigcon.backend.assets.niif_alerts.application;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * Request para registrar una revision anual de activo (HU-ACT-12).
 * Al menos uno de newUsefulLife o newResidualValue puede ser nulo,
 * indicando que ese parametro no cambio en la revision.
 * Si ambos son nulos, se registra como revision CONFIRMED (sin cambios).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnnualReviewRequest {

    @NotNull(message = "El ID del activo es obligatorio.")
    private Long assetId;

    @NotNull(message = "El anio fiscal es obligatorio.")
    private Integer fiscalYear;

    /** Nueva vida util en meses. Null si no cambia. */
    private Integer newUsefulLife;

    /** Nuevo valor residual. Null si no cambia. */
    private BigDecimal newResidualValue;

    /** Justificacion del cambio o de la confirmacion. */
    private String justification;
}

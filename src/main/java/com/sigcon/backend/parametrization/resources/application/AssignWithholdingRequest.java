package com.sigcon.backend.parametrization.resources.application;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request para asignar una retencion al sistema.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignWithholdingRequest {

    @NotNull(message = "El ID de la retencion es obligatorio")
    private Long withholdingId;

    @NotNull(message = "La fecha de inicio de vigencia es obligatoria")
    private LocalDate effectiveFrom;

    /** Fecha fin de vigencia (opcional, null = vigencia indefinida). */
    private LocalDate effectiveTo;
}

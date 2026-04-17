package com.sigcon.backend.general.accounting.application;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Peticion para crear un nuevo periodo contable.
 * Se valida que el anio este entre 2020 y 2100, y el mes entre 1 y 12.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CreatePeriodRequest {

    @NotNull(message = "El anio es obligatorio")
    @Min(value = 2020, message = "El anio minimo es 2020")
    @Max(value = 2100, message = "El anio maximo es 2100")
    private Integer year;

    @NotNull(message = "El mes es obligatorio")
    @Min(value = 1, message = "El mes minimo es 1")
    @Max(value = 12, message = "El mes maximo es 12")
    private Integer month;
}

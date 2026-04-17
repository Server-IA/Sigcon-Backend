package com.sigcon.backend.general.accounting.statements.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para generacion de estados financieros.
 * Define el periodo contable (anio-mes) para el cual se genera el estado.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StatementRequest {

    @NotNull(message = "El anio es obligatorio")
    private Integer year;

    @NotNull(message = "El mes es obligatorio")
    @Min(value = 1, message = "El mes debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes debe estar entre 1 y 12")
    private Integer month;
}

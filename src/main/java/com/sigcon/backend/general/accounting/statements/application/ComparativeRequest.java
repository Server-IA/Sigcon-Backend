package com.sigcon.backend.general.accounting.statements.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para generacion de estados financieros comparativos.
 * Define dos periodos contables para comparar variaciones.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ComparativeRequest {

    @NotNull(message = "El anio del primer periodo es obligatorio")
    private Integer year1;

    @NotNull(message = "El mes del primer periodo es obligatorio")
    @Min(value = 1, message = "El mes debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes debe estar entre 1 y 12")
    private Integer month1;

    @NotNull(message = "El anio del segundo periodo es obligatorio")
    private Integer year2;

    @NotNull(message = "El mes del segundo periodo es obligatorio")
    @Min(value = 1, message = "El mes debe estar entre 1 y 12")
    @Max(value = 12, message = "El mes debe estar entre 1 y 12")
    private Integer month2;
}

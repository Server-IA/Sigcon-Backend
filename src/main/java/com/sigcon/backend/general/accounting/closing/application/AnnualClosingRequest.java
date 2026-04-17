package com.sigcon.backend.general.accounting.closing.application;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para operaciones de cierre contable anual.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnnualClosingRequest {

    @NotNull(message = "El anio es obligatorio")
    private Integer year;

    /** Notas opcionales del cierre anual. */
    private String notes;
}

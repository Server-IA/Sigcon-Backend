package com.sigcon.backend.general.accounting.closing.application;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para generar el asiento de apertura del anio fiscal.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpeningRequest {

    @NotNull(message = "El anio es obligatorio")
    private Integer year;

    /** Notas opcionales del asiento de apertura. */
    private String notes;
}

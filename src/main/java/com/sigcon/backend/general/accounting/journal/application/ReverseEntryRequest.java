package com.sigcon.backend.general.accounting.journal.application;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para reversar un asiento contable contabilizado.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReverseEntryRequest {

    @NotBlank(message = "La descripcion de la reversion es obligatoria.")
    private String description;
}

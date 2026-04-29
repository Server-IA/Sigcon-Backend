package com.sigcon.backend.lists_accounting.types_of_currency.application;

import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.enums.StatusCurrencyType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para la actualización parcial o total de una moneda")
public class CurrencyTypeUpdateRequestDTO {

    @Pattern(regexp = "[A-Z]{3}", message = "Debe ingresar un código ISO válido (ej. USD)")
    @Schema(description = "Nuevo código ISO de la moneda", example = "EUR")
    private String isoCode;

    @Size(min = 1, max = 100, message = "El nombre de la moneda debe tener máximo 100 caracteres")
    // HU-CFG-23 E4 (2026-04-27): rechazar tags HTML/XSS tambien en update.
    @Pattern(regexp = "^[^<>]+$",
            message = "El nombre de la moneda no puede contener los caracteres < o >")
    @Schema(description = "Nuevo nombre de la moneda", example = "Euro")
    private String name;

    @Schema(description = "Nuevo estado de la moneda", example = "ACTIVE")  
    @NotNull(message = "Debe ingresar un estado válido")
    private StatusCurrencyType status;
}

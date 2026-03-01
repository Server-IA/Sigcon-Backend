package com.sigcon.backend.lists_accounting.types_of_currency.application;

import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.enums.StatusCurrencyType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Datos necesarios para registrar una nueva moneda")
public class CurrencyTypeRequestDTO {

    @NotBlank(message = "Debe ingresar un código ISO válido (ej. USD)")
    @Pattern(regexp = "[A-Z]{3}", message = "Debe ingresar un código ISO válido (ej. USD)")
    @Schema(description = "Código ISO de 3 caracteres de la moneda", example = "USD")
    private String isoCode;

    @NotBlank(message = "Debe ingresar un nombre de moneda")
    @Size(min = 1, max = 100, message = "Debe ingresar un nombre de moneda")
    @Schema(description = "Nombre descriptivo de la moneda", example = "Dólar estadounidense")
    private String name;

    @Schema(description = "Estado de la moneda", example = "ACTIVE")
    @NotNull(message = "Debe ingresar un estado válido")
    private StatusCurrencyType status;
}

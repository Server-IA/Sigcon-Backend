package com.sigcon.backend.lists_accounting.types_of_currency.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta con los datos detallados de una moneda")
public class CurrencyTypeResponseDTO {

    @Schema(description = "Identificador único de la moneda", example = "1")
    private Long id;

    @Schema(description = "Código ISO de 3 caracteres", example = "COP")
    private String isoCode;

    @Schema(description = "Nombre de la moneda", example = "Peso Colombiano")
    private String name;

    @Schema(description = "Fecha y hora de registro en el sistema", example = "2024-02-22T10:30:00")
    private LocalDateTime createdAt;
}

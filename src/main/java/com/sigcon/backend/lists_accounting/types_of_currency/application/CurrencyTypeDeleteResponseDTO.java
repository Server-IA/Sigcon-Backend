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
@Schema(description = "Respuesta tras la eliminación lógica de una moneda")
public class CurrencyTypeDeleteResponseDTO {

    @Schema(description = "ID de la moneda eliminada", example = "5")
    private Long id;

    @Schema(description = "Código ISO de la moneda procesada", example = "GBP")
    private String isoCode;

    @Schema(description = "Nombre de la moneda procesada", example = "Libra Esterlina")
    private String name;

    @Schema(description = "Fecha y hora en que se realizó la eliminación", example = "2024-02-22T14:45:00")
    private LocalDateTime deletedAt;

    @Schema(description = "Mensaje descriptivo del resultado de la operación", example = "La moneda ha sido eliminada exitosamente")
    private String message;
}

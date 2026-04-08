package com.sigcon.backend.assets.assets.application;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

@Schema(description = "DTO de creacion de impuestos o retenciones")

public class CreateAssetTaxesRetention {

    @Schema(description = "ID de la regla de impuesto", example = "1")
    @NotNull(message = "Faltan datos requeridos")
    private Long taxRuleId;

    @Schema(description = "Porcentaje de impuesto o retencion", example = "10.00")
    private BigDecimal percentage;

    @Schema(description = "Monto de impuesto o retencion", example = "100.00")
    private BigDecimal amount;
}

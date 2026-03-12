package com.sigcon.backend.assets.assets_depreciation.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

import com.sigcon.backend.assets.assets.application.AssetDepreciationResultDTO;
import com.sigcon.backend.assets.assets.application.AssetSkippedDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta global del proceso de cálculo automático de depreciación")
public class DepreciationCalculationResponseDTO {

    @Schema(description = "Período contable procesado en formato YYYY-MM", example = "2026-03")
    private String period;

    @Schema(description = "Cantidad de activos depreciados exitosamente", example = "5")
    private int processedCount;

    @Schema(description = "Cantidad de activos excluidos del cálculo", example = "2")
    private int skippedCount;

    @Schema(description = "Suma total de depreciación calculada en el período", example = "1250000.00")
    private BigDecimal totalDepreciation;

    @Schema(description = "Detalle de activos depreciados")
    private List<AssetDepreciationResultDTO> results;

    @Schema(description = "Detalle de activos excluidos con la razón de exclusión")
    private List<AssetSkippedDTO> skipped;

    @Schema(description = "Mensaje de resultado del proceso", example = "Depreciación calculada exitosamente")
    private String message;
}

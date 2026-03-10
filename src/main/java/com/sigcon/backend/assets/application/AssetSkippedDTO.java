package com.sigcon.backend.assets.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Activo excluido del proceso de cálculo de depreciación")
public class AssetSkippedDTO {

    /**
     * Razones tipificadas por las que un activo puede ser excluido del cálculo.
     */
    public enum SkipReason {
        /** El activo está en estado DECOMMISSIONED o TRANSFERRED */
        ASSET_INACTIVE,
        /** usefulLifeMonths es null o <= 0 */
        INVALID_USEFUL_LIFE,
        /** depreciationMethod es null o es OTHER (no soportado matemáticamente) */
        NO_DEPRECIATION_METHOD,
        /** No existe una regla de depreciación activa para el método del activo */
        NO_ACTIVE_RULE,
        /** El método UNITS_OF_PRODUCTION no aplica para cálculo por período contable */
        OTHER_METHOD
    }

    @Schema(description = "ID interno del activo excluido", example = "3")
    private Long assetId;

    @Schema(description = "Código único del activo", example = "ACT2026000003")
    private String assetCode;

    @Schema(description = "Nombre del activo", example = "Equipo en baja")
    private String assetName;

    @Schema(description = "Razón de exclusión", example = "ASSET_INACTIVE", allowableValues = {
            "ASSET_INACTIVE",
            "INVALID_USEFUL_LIFE",
            "NO_DEPRECIATION_METHOD",
            "NO_ACTIVE_RULE",
            "OTHER_METHOD"
    })
    private SkipReason reason;
}

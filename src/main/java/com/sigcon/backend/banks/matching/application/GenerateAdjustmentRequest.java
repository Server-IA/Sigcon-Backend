package com.sigcon.backend.banks.matching.application;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * BNK-HU-073: petición para generar el comprobante de ajuste de UN movimiento
 * del extracto. Los overrides (HU-073 E2) permiten al usuario sobrescribir la
 * cuenta contrapartida sugerida con cualquier código PUC del catálogo.
 */
@Data
public class GenerateAdjustmentRequest {

    @NotNull(message = "El movimiento es obligatorio.")
    private Long financialMovementId;

    /** HU-073 E2: código PUC de débito a usar en lugar del sugerido (opcional). */
    private String cuentaDebitoOverride;

    /** HU-073 E2: código PUC de crédito a usar en lugar del sugerido (opcional). */
    private String cuentaCreditoOverride;
}

package com.sigcon.backend.banks.matching.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * BNK-HU-073 E6: generación en lote de ajustes para varios movimientos del
 * mismo tipo. modo = "UNICO" (un comprobante con N líneas) o "INDIVIDUAL"
 * (N comprobantes separados).
 */
@Data
public class GenerateBatchAdjustmentRequest {

    @NotNull(message = "La cuenta bancaria es obligatoria.")
    private Long bankAccountId;

    @NotEmpty(message = "Debe seleccionar al menos un movimiento.")
    private List<Long> financialMovementIds;

    /** UNICO | INDIVIDUAL (default UNICO). */
    private String modo;
}

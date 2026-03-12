package com.sigcon.backend.assets.assets_depreciation.application;

import com.sigcon.backend.assets.assets_depreciation.domain.model.enums.DepreciationMethod;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de respuesta para los endpoints de consulta del histórico de depreciaciones.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Registro histórico de una depreciación aplicada a un activo")
public class ViewAssetDepreciationDTO {

    @Schema(description = "ID del registro histórico", example = "1")
    private Long id;

    @Schema(description = "ID del activo al que pertenece este registro", example = "1")
    private Long assetId;

    @Schema(description = "Código único del activo", example = "ACT2026000001")
    private String assetCode;

    @Schema(description = "Nombre del activo", example = "Impresora laser oficina")
    private String assetName;

    @Schema(description = "Período contable procesado en formato YYYY-MM", example = "2026-03")
    private String depreciationPeriod;

    @Schema(description = "Valor en libros antes de aplicar la depreciación", example = "3200000.00")
    private BigDecimal previousBookValue;

    @Schema(description = "Valor en libros después de aplicar la depreciación", example = "3146666.67")
    private BigDecimal currentBookValue;

    @Schema(description = "Monto depreciado en el período", example = "53333.33")
    private BigDecimal depreciationAmount;

    @Schema(description = "Método de depreciación aplicado", example = "STRAIGHT_LINE")
    private DepretationType depretationType;

    @Schema(description = "Fecha en que se ejecutó el cálculo", example = "2026-03-11")
    private LocalDate calculationDate;

    @Schema(description = "Fecha y hora de creación del registro histórico", example = "2026-03-11T23:30:00")
    private LocalDateTime createdAt;
}

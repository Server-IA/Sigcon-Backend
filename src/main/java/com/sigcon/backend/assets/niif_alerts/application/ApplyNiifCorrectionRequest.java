package com.sigcon.backend.assets.niif_alerts.application;

import com.sigcon.backend.assets.niif_alerts.domain.model.enums.NiifCorrectionType;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplyNiifCorrectionRequest {

    private Long assetId;

    private NiifCorrectionType correctionType;

    private Integer newUsefulLifeMonths;

    private BigDecimal newBookValue;

    /**
     * ACT-14: Nueva regla de depreciacion (ID) a aplicar cuando la correccion
     * es de tipo {@code DEPRECIATION_METHOD_CHANGE}. La regla apunta a un metodo
     * (LINEAR/DECREASING/...) y tasa definidos en CFG.
     */
    private Long newDepretationRuleId;

    private String observations;

}
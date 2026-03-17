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

    private String observations;

}
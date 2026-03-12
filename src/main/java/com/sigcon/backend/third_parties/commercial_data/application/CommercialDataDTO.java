package com.sigcon.backend.third_parties.commercial_data.application;

import java.math.BigDecimal;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommercialDataDTO {

    private Long Id; 
    private Long thirdPartyId;
    private Long paymentTermId;
    private BigDecimal limitCredit;
    private RiskSegmentation riskLevel;
}

package com.sigcon.backend.third_parties.commercial_data.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.resources.application.PaymentTermsDTO;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommercialDataResponse {

    private Long Id; 
    private Long thirdPartyId;
    private PaymentTermsDTO paymentTerm; 
    private BigDecimal limitCredit;
    private RiskSegmentation riskLevel; 
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

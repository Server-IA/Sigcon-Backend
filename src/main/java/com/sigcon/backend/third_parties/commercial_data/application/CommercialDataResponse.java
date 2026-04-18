package com.sigcon.backend.third_parties.commercial_data.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.resources.application.PaymentTermsDTO;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;

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
    private ThirdPartyDTO thirdParty;
    private PaymentTermsDTO paymentTerm;
    private BigDecimal limitCredit;
    private RiskSegmentation riskLevel;
    private Long currencyId;
    private String currencyIsoCode;
    private String currencyName;
    private LocalDate validityFrom;
    private LocalDate validityTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

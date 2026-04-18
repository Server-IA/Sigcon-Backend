package com.sigcon.backend.third_parties.commercial_data.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommercialDataRequest {

    @NotNull(message = "debe diligenciar todos los campos obloigatorios")
    private Long thirdPartyId;
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    private Long paymentTermId;
    @Positive(message = "el limite de credito debe de ser un valor positivo")
    private BigDecimal limitCredit;
    private RiskSegmentation riskLevel;

    /** TER-11: ID de la moneda asociada al limite de credito */
    private Long currencyId;

    /** TER-12: Fecha inicio de vigencia */
    private LocalDate validityFrom;

    /** TER-12: Fecha fin de vigencia */
    private LocalDate validityTo;
}

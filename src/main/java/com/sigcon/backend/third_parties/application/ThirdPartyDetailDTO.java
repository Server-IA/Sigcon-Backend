package com.sigcon.backend.third_parties.application;

import com.sigcon.backend.third_parties.domain.model.enums.PersonType;
import com.sigcon.backend.third_parties.domain.model.enums.TaxRegime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyDetailDTO {
    private GeneralTab general;
    private FiscalTab fiscal;
    private CommercialTab commercial;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneralTab {
        private Long id;
        private String thirdPartyCode;
        private String nit;
        private String dv;
        private String businessName;
        private PersonType personType;
        private List<Long> roleIds;
        private List<String> roleNames;
        private Long statusId;
        private String statusName;
        private String blockingReason;
        private String city;
        private String department;
        private String address;
        private String country;
        private String phone;
        private String email;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FiscalTab {
        private TaxRegime taxRegime;
        private String fiscalResponsibilities;
        private String withholdingInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommercialTab {
        private BigDecimal creditLimit;
        private String paymentTerms;
        private String marketSegment;
    }
}

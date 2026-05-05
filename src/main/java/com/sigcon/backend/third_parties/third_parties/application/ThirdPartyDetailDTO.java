package com.sigcon.backend.third_parties.third_parties.application;

import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;

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
    /** HU-TER-01 E2.0 (Bloque AN, 2026-05-04): cuarta pestaña Bancaria con
     *  las cuentas bancarias asociadas al tercero. */
    private BankingTab banking;

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
        private List<ThirdPartyRoleCatalogDTO> roles;
        private ThirdPartyStatusCatalogDTO status;
        private String blockingReason;
        private MunicipalityDTO municipality;
        private TypeOrganizationDTO typeOrganization;
        private List<ThirdContactDTO> contacts;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FiscalTab {
        private TypeRegimenDTO typeRegimen;
        private List<WithholdingDTO> withholdings;
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

    /**
     * HU-TER-01 E2.0 (Bloque AN, 2026-05-04): pestaña Bancaria — listado de
     * cuentas bancarias asociadas al tercero. Si no tiene ninguna, viene
     * con accounts=[] y count=0 para que el frontend muestre el estado vacio.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankingTab {
        private Integer count;
        private List<BankingAccount> accounts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankingAccount {
        private Long linkId;
        private Long bankAccountId;
        private String accountNumber;
        private String accountType;
        private String bankName;
        private Boolean isPrimary;
    }
}

package com.sigcon.backend.third_parties.bank_accounts.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para vinculaciones de cuentas bancarias a terceros.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ThirdPartyBankAccountDTO {

    private Long id;
    private Long thirdPartyId;
    private Long bankAccountId;
    private String bankAccountCode;
    private String bankName;
    private String accountNumber;
    private Boolean isPrimary;
}

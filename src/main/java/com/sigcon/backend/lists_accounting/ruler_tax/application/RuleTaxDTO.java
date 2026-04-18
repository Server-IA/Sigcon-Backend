package com.sigcon.backend.lists_accounting.ruler_tax.application;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.StatusRulerTax;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.TypeRulerTax;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleTaxDTO {

    private Long id;
    private String name;
    private Double percentage;
    private String description;
    private String scope;
    private LocalDate dateStart;
    private LocalDate dateEnd;

    private TypeRulerTax typeRulerTax;
    private StatusRulerTax statusRulerTax;

    private AccountingAccountDTO accountingAccount;

    private Double minAmountUvt;
    private Double uvtValueYear;

}

package com.sigcon.backend.banks.bankaccounts.application;

import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus;
import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de detalle completo de cuenta bancaria")
public class BankAccountDetailDTO {

    private Long id;
    private String code;
    private String accountNumberMasked;
    private String accountName;
    private BankAccountType accountType;
    private Long bankId;
    private String bankName;
    private Long currencyTypeId;
    private String currencyCode;
    private BigDecimal initialBalance;
    private Long chartOfAccountId;
    private String chartOfAccountCode;
    private String chartOfAccountName;
    private Long companyId;
    private String companyName;
    private BankAccountStatus status;
    private LocalDate openingDate;
    private LocalDate lastReconciliationDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    private Long bankBranchId;
    private String branchName;
    private String accountExecutive;
    private String bankPhone;
    private String description;
    private Boolean allowsOverdraft;
    private BigDecimal creditLimit;
    private Boolean notifyLowBalance;
    private BigDecimal minimumBalance;
    private Boolean handlesCheckbook;
    private Long costCenterId;
    private Long bookId;
    private LocalDate closingDate;
}

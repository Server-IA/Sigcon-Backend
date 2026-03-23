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
@Schema(description = "DTO de respuesta de cuenta bancaria")
public class BankAccountDTO {

    @Schema(description = "ID de la cuenta bancaria")
    private Long id;

    @Schema(description = "Código interno de la cuenta")
    private String code;

    @Schema(description = "Número de cuenta enmascarado (****1234)")
    private String accountNumberMasked;

    @Schema(description = "Nombre de la cuenta")
    private String accountName;

    @Schema(description = "Tipo de cuenta")
    private BankAccountType accountType;

    @Schema(description = "ID del banco")
    private Long bankId;

    @Schema(description = "Nombre del banco")
    private String bankName;

    @Schema(description = "ID del tipo de moneda")
    private Long currencyTypeId;

    @Schema(description = "Código ISO de la moneda")
    private String currencyCode;

    @Schema(description = "Saldo inicial")
    private BigDecimal initialBalance;

    @Schema(description = "ID de la cuenta contable")
    private Long chartOfAccountId;

    @Schema(description = "Código de la cuenta contable")
    private String chartOfAccountCode;

    @Schema(description = "Nombre de la cuenta contable")
    private String chartOfAccountName;

    @Schema(description = "ID de la empresa")
    private Long companyId;

    @Schema(description = "Nombre de la empresa")
    private String companyName;

    @Schema(description = "Estado de la cuenta")
    private BankAccountStatus status;

    @Schema(description = "Fecha de apertura")
    private LocalDate openingDate;

    @Schema(description = "Fecha de última conciliación (placeholder)")
    private LocalDate lastReconciliationDate;

    @Schema(description = "Fecha de creación")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de eliminación lógica")
    private LocalDateTime deletedAt;
}

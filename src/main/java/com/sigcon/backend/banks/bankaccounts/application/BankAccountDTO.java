package com.sigcon.backend.banks.bankaccounts.application;

import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus;
import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountType;
import com.sigcon.backend.banks.banks.application.BankBranchDTO;
import com.sigcon.backend.banks.banks.application.BankDTO;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;

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

    @Schema(description = "Banco")
    private BankDTO bankDTO;

    @Schema(description = "Sucursal bancaria")
    private BankBranchDTO bankBranchDTO;
    
    @Schema(description = "Moneda de la cuenta")
    private CurrencyTypeResponseDTO currencyTypeDTO;

    @Schema(description = "Saldo inicial")
    private BigDecimal initialBalance;

    /**
     * QA-BLOQUE-AY (2026-05-06): saldo actual calculado como
     * initialBalance + sum(financial_movements.amount). Antes la UI mostraba
     * solo el saldo de apertura, sin reflejar pagos/depositos posteriores. El
     * contador veia $25M perpetuamente aunque hubiera pagado facturas.
     */
    @Schema(description = "Saldo actual (initialBalance + sum movimientos)")
    private BigDecimal currentBalance;

    @Schema(description = "Cuenta contable")
    private AccountingAccountDTO accountingAccountDTO;

    @Schema(description = "Centro de costo")
    private CostCenterDTO costCenterDTO;

    @Schema(description = "Ejecutivo de cuenta")
    private String accountExecutive;

    @Schema(description = "Estado de la cuenta")
    private BankAccountStatus status;

    @Schema(description = "Fecha de apertura")
    private LocalDate openingDate;

    @Schema(description = "Fecha de última conciliación registrada (cierre de extracto)")
    private LocalDate lastReconciliationDate;

    @Schema(description = "Fecha de creación")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de eliminación lógica")
    private LocalDateTime deletedAt;

    // QA HU-001 E5 / HU-002 E2/E3 / HU-008 E1: campos faltantes en el DTO.
    // Antes el toDto NO los incluia y al re-abrir el modal de edicion los
    // toggles y descripcion aparecian vacios (NO persistian aparentemente).

    @Schema(description = "Descripcion libre de la cuenta")
    private String description;

    @Schema(description = "Permite sobregiro")
    private Boolean allowsOverdraft;

    @Schema(description = "Limite de credito (cupo)")
    private BigDecimal creditLimit;

    @Schema(description = "Notificar saldo minimo")
    private Boolean notifyLowBalance;

    @Schema(description = "Saldo minimo configurado")
    private BigDecimal minimumBalance;

    @Schema(description = "Telefono de banco / sucursal")
    private String bankPhone;

    // QA Bloque AU (2026-05-06) — Bug 1 (toggle Maneja chequera): el DTO NO
    // exponia este campo, asi que el frontend lo recibia undefined y al
    // re-abrir el detalle/edicion siempre se veia "No". Ahora se incluye en
    // toDto.
    @Schema(description = "Indica si la cuenta maneja chequera (cheques fisicos/virtuales)")
    private Boolean handlesCheckbook;

    @Schema(description = "Indica si la cuenta tiene chequeras o movimientos asociados (frontend deshabilita campos criticos)")
    private Boolean hasAssociatedAccounts;
}

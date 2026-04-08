package com.sigcon.backend.vouchers.application;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.sigcon.backend.assets.assets.application.ViewAssetsDTO;
import com.sigcon.backend.banks.bankaccounts.application.BankAccountDTO;
import com.sigcon.backend.banks.cash_management.application.CashDTO;
import com.sigcon.backend.banks.checks.application.CheckDTO;
import com.sigcon.backend.parametrization.resources.application.PaymentFormsDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de visualizacion del comprobante")
public class VoucherDTO {

    @Schema(description = "ID interno del comprobante", example = "1")
    private Long id;

    @Schema(description = "Numero del comprobante", example = "1")
    private BigInteger number;

    @Schema(description = "Fecha del comprobante", example = "2026-01-01")
    private LocalDate date;

    @Schema(description = "Monto del comprobante", example = "100000.00")
    private BigDecimal amount;

    @Schema(description = "Descripcion del comprobante", example = "Compra de activo")
    private String description;

    @Schema(description = "Tipo de comprobante", example = "1")
    private VoucherTypeDTO voucherType;

    @Schema(description = "Metodo de pago", example = "1")
    private PaymentFormsDTO paymentForm;

    @Schema(description = "Cuenta bancaria", example = "1")
    private BankAccountDTO bankAccount;

    @Schema(description = "Cuenta de caja", example = "1")
    private CashDTO cashAccount;

    @Schema(description = "Cheque", example = "1")
    private CheckDTO check;

    @Schema(description = "Activo", example = "1")
    private ViewAssetsDTO asset;

}

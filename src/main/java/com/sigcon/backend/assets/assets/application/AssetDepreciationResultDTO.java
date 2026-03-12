package com.sigcon.backend.assets.assets.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.sigcon.backend.assets.assets_depreciation.domain.model.enums.DepreciationMethod;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resultado de depreciación calculada para un activo individual")
public class AssetDepreciationResultDTO {

    @Schema(description = "ID interno del activo", example = "1")
    private Long assetId;

    @Schema(description = "Código único del activo", example = "ACT2026000001")
    private String assetCode;

    @Schema(description = "Nombre del activo", example = "Impresora laser oficina")
    private String assetName;

    @Schema(description = "Método de depreciación aplicado", example = "STRAIGHT_LINE")
    private DepretationType depreciationMethod;

    @Schema(description = "Monto de depreciación calculado para el período", example = "53333.33")
    private BigDecimal depreciationAmount;

    @Schema(description = "Valor en libros antes del cálculo", example = "3200000.00")
    private BigDecimal previousBookValue;

    @Schema(description = "Valor en libros actualizado después del cálculo", example = "3146666.67")
    private BigDecimal currentBookValue;

    @Schema(description = "Proveedor del activo", example = "TERCERO DEMO PROVEEDOR SAS")
    private ThirdPartyDTO supplier;

    @Schema(description = "Cuenta contable del activo", example = "1504")
    private AccountingAccountDTO accountingAccount;

    @Schema(description = "Fecha en que se ejecutó el cálculo", example = "2026-03-08")
    private LocalDate calculationDate;
}

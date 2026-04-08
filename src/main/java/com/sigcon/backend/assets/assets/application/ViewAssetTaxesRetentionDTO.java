package com.sigcon.backend.assets.assets.application;

import java.math.BigDecimal;

import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.application.RuleTaxDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de visualizacion de impuestos o retenciones")

public class ViewAssetTaxesRetentionDTO {

    @Schema(description = "ID de la retencion de impuesto", example = "1")
    private Long id;

    @Schema(description = "ID de la regla de impuesto", example = "1")
    private RuleTaxDTO taxRule;
    
    @Schema(description = "Porcentaje de impuesto o retencion", example = "10.00")
    private BigDecimal percentage;

    @Schema(description = "Monto de impuesto o retencion", example = "100.00")
    private BigDecimal amount;



}

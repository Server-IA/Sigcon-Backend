package com.sigcon.backend.nomina.application;

import com.sigcon.backend.nomina.domain.model.PayrollConcept;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Concepto de nomina (HU-NOM-02).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Concepto de nomina con formula y cuentas PUC (HU-NOM-02)")
public class PayrollConceptDTO {

    @Schema(description = "ID interno", example = "1")
    private Long id;

    @Schema(description = "Codigo unico (UPPER_SNAKE)", example = "SALUD_EMPLEADO")
    private String code;

    @Schema(description = "Nombre descriptivo", example = "Aporte salud empleado")
    private String name;

    @Schema(description = "Tipo", allowableValues = {"EARNING", "DEDUCTION", "EMPLOYER_CONTRIBUTION"},
            example = "DEDUCTION")
    private String conceptType;

    @Schema(description = "Base de calculo", allowableValues = {"SALARY", "IBC", "FIXED", "CUSTOM"},
            example = "IBC")
    private String baseCalculation;

    @Schema(description = "Porcentaje (ej 4.00 = 4%)", example = "4.00")
    private BigDecimal percentage;

    @Schema(description = "Monto fijo si aplica", example = "162000.00")
    private BigDecimal fixedAmount;

    @Schema(description = "Expresion libre si baseCalculation=CUSTOM")
    private String formulaExpression;

    @Schema(description = "FK accounting_accounts - cuenta debito", example = "45")
    private Long accountingAccountDebitId;

    @Schema(description = "FK accounting_accounts - cuenta credito", example = "87")
    private Long accountingAccountCreditId;

    @Schema(description = "Referencia legal", example = "Ley 100/1993 Art. 204")
    private String legalReference;

    @Schema(description = "Estado", allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
    private String status;

    public static PayrollConceptDTO from(PayrollConcept c) {
        return PayrollConceptDTO.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .conceptType(c.getConceptType())
                .baseCalculation(c.getBaseCalculation())
                .percentage(c.getPercentage())
                .fixedAmount(c.getFixedAmount())
                .formulaExpression(c.getFormulaExpression())
                .accountingAccountDebitId(c.getAccountingAccountDebitId())
                .accountingAccountCreditId(c.getAccountingAccountCreditId())
                .legalReference(c.getLegalReference())
                .status(c.getStatus())
                .build();
    }
}

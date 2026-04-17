package com.sigcon.backend.nomina.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request para crear/actualizar concepto de nomina (HU-NOM-02).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Crear o actualizar concepto de nomina")
public class CreatePayrollConceptRequest {

    @NotBlank(message = "code es obligatorio")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$",
            message = "code debe ser MAYUSCULAS, letras/digitos/underscore, iniciar con letra")
    @Schema(description = "Codigo unico UPPER_SNAKE (ej SALUD_EMPLEADO)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "BONIF_DESEMPENO")
    private String code;

    @NotBlank(message = "name es obligatorio")
    @Size(max = 200)
    @Schema(description = "Nombre descriptivo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "conceptType es obligatorio")
    @Pattern(regexp = "EARNING|DEDUCTION|EMPLOYER_CONTRIBUTION")
    @Schema(description = "Tipo", allowableValues = {"EARNING", "DEDUCTION", "EMPLOYER_CONTRIBUTION"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String conceptType;

    @Pattern(regexp = "SALARY|IBC|FIXED|CUSTOM")
    @Schema(allowableValues = {"SALARY", "IBC", "FIXED", "CUSTOM"})
    private String baseCalculation;

    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Porcentaje (4.00 = 4%)", example = "4.00")
    private BigDecimal percentage;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal fixedAmount;

    private String formulaExpression;

    @Schema(description = "FK accounting_accounts - cuenta debito del JE")
    private Long accountingAccountDebitId;

    @Schema(description = "FK accounting_accounts - cuenta credito del JE")
    private Long accountingAccountCreditId;

    @Size(max = 100)
    private String legalReference;

    @Pattern(regexp = "ACTIVE|INACTIVE")
    @Schema(allowableValues = {"ACTIVE", "INACTIVE"}, example = "ACTIVE")
    private String status;
}

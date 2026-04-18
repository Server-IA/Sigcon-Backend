package com.sigcon.backend.lists_accounting.ruler_tax.application;

import java.time.LocalDate;

import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.StatusRulerTax;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.TypeRulerTax;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateRuleTaxDTO {
    @NotBlank(message = "El nombre es requerido")
    private String name;
    @NotNull(message = "El porcentaje es requerido")
    @DecimalMin(value = "0.0", inclusive = false, message = "El porcentaje debe ser mayor a 0")
    @DecimalMax(value = "100.0", message = "El porcentaje debe ser menor o igual a 100" )
    private Double percentage;
    @NotBlank(message = "La descripción es requerida")
    private String description;
    @NotBlank(message = "El alcance es requerido")
    private String scope;
    @NotNull(message = "La fecha de inicio es requerida")
    private LocalDate dateStart;
    @NotNull(message = "La fecha de fin es requerida")
    private LocalDate dateEnd;
    @NotNull(message = "El tipo de regla de impuesto es requerido")
    private TypeRulerTax typeRulerTax;
    private StatusRulerTax statusRulerTax;
    @NotNull(message = "La cuenta contable es requerida")
    private Long accountingAccountId;

    private Double minAmountUvt;
    private Double uvtValueYear;
}

package com.sigcon.backend.lists_accounting.depretation_rules.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Datos requeridos para poder crear una regla de depreciación")
public class CreateDepretationRuleRequest {

    @Schema(description = "Nombre de la Regla de depreciación", example = "Depreciacion Lineal Equipos", maxLength = 100)
    @NotBlank(message = "Debe diligenciar todos los campos obligatorios")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String name; 

    @Schema(description = "Descripcion estructurada con base de calculo, parametros, excepciones y norma aplicable")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    private DescriptionStructuredDTO descriptionStructured;

    @Schema(description = "Tipo de Depreciación", example = "LINEAR", allowableValues = {"LINEAR","DECREASING","ACCELERATED","PRODUCTION_UNITS","MINIMUN_USEFUL_LIFE"})
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    private DepretationType depretationType;

    @Schema(description = "ID de la Cuenta Contable asociada", example = "1")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    private Long accountingAccountId; 

    @Schema(description = "Tasa de Depreciacion Numerico, con porcentajes de 0-100 con hasta 2 decimales", example = "20.00")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    @DecimalMin(value = "0.00", message = "La tasa debe de ser un porcentaje entre 0 y 100 con hasta 2 decimales")
    @DecimalMax(value = "100.00", message = "La tasa debe de ser un porcentaje entre 0 y 100 con hasta 2 decimales")
    @Digits(integer = 3, fraction = 2, message = "La tasa debe de ser un porcentaje entre 0 y 100 con hasta 2 decimales")
    private BigDecimal depretationRate; 

    @Schema(description = "Vida Util en años segun el Tipo de depreciacion selecionada", example = "5")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    @Min(value = 1, message = "Vida util no valida para el metodo selecionado")
    private Integer usefulLifeYears;

    @Schema(description = "Valor residual del activo al final de su vida util", example = "0.00")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    @DecimalMin(value = "0.00", message = "El valor resiudal debe ser mayor o igual a 0 ")
    private BigDecimal residualValue;

    @Schema(description = "Fecha de vigencia efectiva de la regla en formato (dd/MM/yyy)", example = "01/01/2026")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    @JsonFormat(pattern = "dd-MM-yyyy")
    private LocalDate effectiveDate; 

}

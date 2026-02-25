package com.sigcon.backend.depretation_rules.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sigcon.backend.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.depretation_rules.domain.model.enums.DepretationType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Datos requeridos para actualizar una regla de depreciación existente")
public class UpdateDepretationRuleRequest {

    @Schema(description = "Identificador único de la regla de depreciación: Identificador único que el sistema asigna a la regla de depreciación.", example = "1")
    @NotNull(message = "El identificador es obligatorio")
    private Long id;

    @Schema(description = "Nombre de la regla de depreciacion", example = "Depreciación Lineal Equipos Oficina", maxLength = 100)
    @NotBlank(message = "Debe diligenciar todos los campos obligatorios")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String name;

    @Schema(description = "Tipo de Depreciación", example = "LINEAR", allowableValues = {"LINEAR","DECREASING","ACCELERATED","PRODUCTION_UNITS","MINIMUN_USEFUL_LIFE"})
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    private DepretationType depretationType;

    @Schema(description = "Tasa de Depreciacion Numerico, con porcentajes de 0-100 con hasta 2 decimales", example = "20.00")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    @DecimalMin(value = "0.00", message = "La tasa debe ser un porcentaje entre 0 y 100 con hasta 2 decimales")
    @DecimalMax(value = "100.00", message = "La tasa debe ser un porcentaje entre 0 y 100 con hasta 2 decimales")
    @Digits(integer = 3, fraction = 2, message = "La tasa debe ser un porcentaje entre 0 y 100 con hasta 2 decimales")
    private BigDecimal depretationRate;

    @Schema(description = "Vida Util en años segun el Tipo de depreciacion selecionada", example = "5")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    @Min(value = 1, message = "Vida útil no válida para el método seleccionado")
    private Integer usefulLifeYears;

    @Schema(description = "Valor residual del activo al final de su vida util", example = "0.00")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    @DecimalMin(value = "0.00", message = "El valor residual debe ser mayor o igual a 0")
    private BigDecimal residualValue;

    @Schema(description = "Fecha de vigencia efectiva de la regla en formato (dd/MM/yy)", example = "01/01/2026")
    @NotNull(message = "Debe diligenciar todos los campos obligatorios")
    private DepretationStatus status;

    // Nota: accountingAccountId, effectiveDate y descriptionStructured son solo lectura 
    // para evitar inconsistencias al editar (según CFG-RF-15)
}

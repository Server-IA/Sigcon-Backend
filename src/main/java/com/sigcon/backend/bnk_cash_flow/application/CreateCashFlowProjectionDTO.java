package com.sigcon.backend.bnk_cash_flow.application;

import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionPeriodicity;
import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BNK-RF-29 — DTO de entrada para creación de proyecciones de flujo de caja.
 *
 * Nota: finalBalance NO se recibe del cliente, se calcula en el servicio
 * como: finalBalance = initialBalance + netFlow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de entrada para registrar una proyección de flujo de caja")
public class CreateCashFlowProjectionDTO {

    @NotBlank(message = "El nombre de la proyección es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    @Schema(description = "Nombre único de la proyección", example = "Proyección Q1 2026")
    private String name;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    @Schema(description = "Descripción opcional de la proyección", example = "Proyección trimestral del primer cuarto de 2026")
    private String description;

    @NotNull(message = "La fecha de inicio es obligatoria")
    @Schema(description = "Fecha de inicio del período (debe ser anterior a endDate)", example = "2026-01-01")
    private LocalDate startDate;

    @NotNull(message = "La fecha de fin es obligatoria")
    @Schema(description = "Fecha de fin del período (debe ser posterior a startDate)", example = "2026-03-31")
    private LocalDate endDate;

    @NotNull(message = "La periodicidad es obligatoria")
    @Schema(description = "Periodicidad del análisis de flujo", example = "MENSUAL")
    private ProjectionPeriodicity periodicity;

    @NotNull(message = "El tipo de proyección es obligatorio")
    @Schema(description = "Tipo de proyección", example = "NETA")
    private ProjectionType projectionType;

    @NotNull(message = "El saldo inicial es obligatorio")
    @DecimalMin(value = "0.0", message = "El saldo inicial no puede ser negativo")
    @Schema(description = "Saldo inicial del período", example = "50000000.00")
    private BigDecimal initialBalance;

    @NotNull(message = "El flujo neto es obligatorio")
    @Schema(description = "Flujo neto del período (puede ser negativo en proyecciones de egresos)", example = "12000000.00")
    private BigDecimal netFlow;

    @NotBlank(message = "La moneda (ISO 4217) es obligatoria")
    @Size(min = 3, max = 3, message = "La moneda debe ser un código ISO 4217 de 3 caracteres")
    @Schema(description = "Código de moneda ISO 4217", example = "COP")
    private String currency;
}

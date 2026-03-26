package com.sigcon.backend.bnk_cash_flow.application;

import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionPeriodicity;
import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionStatus;
import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionType;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BNK-RF-32 — DTO de salida para consulta y listado de proyecciones de flujo de caja.
 *
 * Incluye finalBalance (calculado por el sistema) y omite deletedAt
 * para no exponer internos de la eliminación lógica al cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de respuesta con la información completa de una proyección de flujo de caja")
public class ViewCashFlowProjectionDTO {

    @Schema(description = "Identificador único de la proyección", example = "1")
    private Long id;

    @Schema(description = "Nombre único de la proyección", example = "Proyección Q1 2026")
    private String name;

    @Schema(description = "Descripción de la proyección", example = "Proyección trimestral del primer cuarto de 2026")
    private String description;

    @Schema(description = "Fecha de inicio del período", example = "2026-01-01")
    private LocalDate startDate;

    @Schema(description = "Fecha de fin del período", example = "2026-03-31")
    private LocalDate endDate;

    @Schema(description = "Periodicidad de la proyección", example = "MENSUAL")
    private ProjectionPeriodicity periodicity;

    @Schema(description = "Tipo de proyección", example = "NETA")
    private ProjectionType projectionType;

    @Schema(description = "Saldo inicial del período", example = "50000000.00")
    private BigDecimal initialBalance;

    @Schema(description = "Flujo neto del período", example = "12000000.00")
    private BigDecimal netFlow;

    @Schema(description = "Saldo final calculado: initialBalance + netFlow", example = "62000000.00")
    private BigDecimal finalBalance;

    @Schema(description = "Código de moneda ISO 4217", example = "COP")
    private String currency;

    @Schema(description = "Estado actual de la proyección", example = "BORRADOR")
    private ProjectionStatus status;

    @Schema(description = "Último motivo de modificación registrado", example = "Ajuste de cifras por revisión contable")
    private String modificationReason;

    @Schema(description = "Fecha de creación del registro", example = "2026-03-21T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de última actualización", example = "2026-03-21T11:40:10")
    private LocalDateTime updatedAt;
}

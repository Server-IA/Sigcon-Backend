package com.sigcon.backend.banks.bnk_cash_flow.application;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.sigcon.backend.banks.bnk_cash_flow.domain.model.enums.ProjectionPeriodicity;
import com.sigcon.backend.banks.bnk_cash_flow.domain.model.enums.ProjectionStatus;
import com.sigcon.backend.banks.bnk_cash_flow.domain.model.enums.ProjectionType;

/**
 * BNK-RF-30 — DTO de entrada para modificación de proyecciones de flujo de caja.
 *
 * Todos los campos son opcionales (patch parcial).
 * modificationReason es obligatorio en la capa de servicio cuando status = APROBADA.
 *
 * Restricciones validadas en servicio:
 * - No se puede modificar si status = EJECUTADA o INACTIVA.
 * - Si status = APROBADA, se requiere modificationReason.
 * - endDate debe ser posterior a startDate si ambos se envían.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de entrada para modificar una proyección de flujo de caja")
public class UpdateCashFlowProjectionDTO {

    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    @Schema(description = "Nuevo nombre de la proyección", example = "Proyección Q1 2026 revisada")
    private String name;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    @Schema(description = "Nueva descripción de la proyección")
    private String description;

    @Schema(description = "Nueva fecha de inicio del período", example = "2026-01-01")
    private LocalDate startDate;

    @Schema(description = "Nueva fecha de fin del período", example = "2026-03-31")
    private LocalDate endDate;

    @Schema(description = "Nueva periodicidad", example = "MENSUAL")
    private ProjectionPeriodicity periodicity;

    @Schema(description = "Nuevo tipo de proyección", example = "NETA")
    private ProjectionType projectionType;

    @Schema(description = "Nuevo saldo inicial", example = "60000000.00")
    private BigDecimal initialBalance;

    @Schema(description = "Nuevo flujo neto del período", example = "15000000.00")
    private BigDecimal netFlow;

    @Size(min = 3, max = 3, message = "La moneda debe ser un código ISO 4217 de 3 caracteres")
    @Schema(description = "Código de moneda ISO 4217", example = "COP")
    private String currency;

    @Schema(description = "Nuevo estado de la proyección (solo BORRADOR -> APROBADA es permitido via este campo)", example = "APROBADA")
    private ProjectionStatus status;

    /**
     * BNK-RF-30 — Obligatorio cuando la proyección tiene status = APROBADA.
     * Describe el motivo del cambio a registrar.
     */
    @Size(max = 500, message = "El motivo de modificación no puede superar los 500 caracteres")
    @Schema(description = "Motivo de modificación (obligatorio si la proyección está APROBADA)", example = "Ajuste de cifras por revisión contable")
    private String modificationReason;
}

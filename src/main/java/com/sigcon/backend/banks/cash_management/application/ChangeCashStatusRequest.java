package com.sigcon.backend.banks.cash_management.application;

import java.time.LocalDate;

import com.sigcon.backend.banks.cash_management.domain.model.enums.CashStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos requeridos para cambiar el estado de una caja")
public class ChangeCashStatusRequest {

    @Schema(description = "El estado destino de la caja", example = "INACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "CLOSED"})
    @NotNull(message = "El estado destino es obligatorio")
    private CashStatus status;
    @Schema(description = "Motivo del cambio de estado (obligatorio para INACTIVE y CLOSED, mínimo 10 caracteres)", example = "Caja fuera de operación por remodelación")
    @Size(min = 10, message = "el motivo debe de tener minimo 10 caracteres")
    private String reason;
    @Schema(description = "Fecha de cierre de la caja (solo aplica si el estado de la caja es CLOSED)", example = "2026-12-31")
    private LocalDate closingDate;
}

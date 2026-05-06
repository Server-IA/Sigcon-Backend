package com.sigcon.backend.invoices.purchase_orders.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QA-BLOQUE-AY HU-AP-21 (2026-05-05): request para crear una devolucion
 * parcial o total a partir de una recepcion existente.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Datos para registrar una devolucion de mercancia")
public class CreateGoodsReturnRequest {

    @NotNull(message = "La fecha de la devolucion es obligatoria")
    @Schema(description = "Fecha en que se realiza la devolucion")
    private LocalDate returnDate;

    @NotBlank(message = "Debe ingresar el motivo de la devolucion")
    @Size(min = 20, message = "El motivo debe tener al menos 20 caracteres")
    @Schema(description = "Motivo de la devolucion (min. 20 chars)")
    private String reason;

    @NotEmpty(message = "Debe especificar al menos una linea a devolver")
    @Schema(description = "Lineas a devolver (cantidad por linea de recepcion)")
    private List<Line> lines;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Line {
        @NotNull(message = "La linea de recepcion es obligatoria")
        private Long goodsReceiptLineId;
        @NotNull(message = "La cantidad a devolver es obligatoria")
        private BigDecimal quantityReturned;
        private String notes;
    }
}

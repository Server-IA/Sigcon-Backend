package com.sigcon.backend.accounts_receivable.sales_invoices.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear una factura de venta (FV).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesInvoiceRequest {

    @Schema(description = "ID del tercero (cliente)", example = "1")
    @NotNull(message = "El cliente es requerido")
    private Long thirdPartyId;

    @Schema(description = "Fecha de emision de la factura", example = "2026-04-13")
    @NotNull(message = "La fecha de factura es requerida")
    private LocalDate invoiceDate;

    @Schema(description = "Fecha de vencimiento", example = "2026-05-13")
    @NotNull(message = "La fecha de vencimiento es requerida")
    private LocalDate dueDate;

    @Schema(description = "ID de la moneda (null = COP)", example = "1")
    private Long currencyId;

    @Schema(description = "Tasa de cambio respecto al COP. Obligatorio si moneda != COP", example = "4200.00")
    private BigDecimal exchangeRate;

    @Schema(description = "ID de la forma de pago", example = "1")
    private Long paymentFormId;

    @Schema(description = "Numero de resolucion DIAN", example = "18760000001")
    private String resolutionNumber;

    @Schema(description = "Notas u observaciones")
    private String notes;

    @Schema(description = "Lineas de detalle de la factura")
    @NotEmpty(message = "La factura debe tener al menos una linea")
    @Valid
    @Builder.Default
    private List<CreateSalesInvoiceLineRequest> lines = new ArrayList<>();
}

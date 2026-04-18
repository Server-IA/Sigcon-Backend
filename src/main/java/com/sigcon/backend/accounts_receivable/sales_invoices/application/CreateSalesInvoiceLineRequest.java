package com.sigcon.backend.accounts_receivable.sales_invoices.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear una linea de factura de venta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesInvoiceLineRequest {

    @Schema(description = "ID del item/producto", example = "1")
    private Long itemId;

    @Schema(description = "Descripcion del item", example = "Servicio de consultoria")
    private String description;

    @Schema(description = "Cantidad", example = "1.0")
    @NotNull(message = "La cantidad es requerida")
    private BigDecimal quantity;

    @Schema(description = "Precio unitario", example = "100000.00")
    @NotNull(message = "El precio unitario es requerido")
    private BigDecimal unitPrice;

    @Schema(description = "Descuento por linea", example = "0")
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    /** IDs de reglas tributarias aplicables (TAX y WITHHOLDING). */
    @Schema(description = "IDs de reglas tributarias a aplicar")
    @Builder.Default
    private List<Long> taxRuleIds = new ArrayList<>();
}

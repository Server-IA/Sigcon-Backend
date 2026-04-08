package com.sigcon.backend.invoices.application;

import com.sigcon.backend.assets.assets.application.ViewAssetsDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

@Tag(name = "Línea de factura", description = "Datos de la línea de la factura")

public class LineInvoiceDTO {

    @Schema(description = "ID de la línea de la factura")
    private Long id;

    @Schema(description = "Activos asociados a la línea de la factura")
    private ViewAssetsDTO asset;

    @Schema(description = "Cantidad de la línea de la factura")
    private Double quantity;

    @Schema(description = "Precio unitario de la línea de la factura")
    private Double unitPrice;   

    @Schema(description = "Descuento de la línea de la factura")
    private Double discount;

    @Schema(description = "Impuesto de la línea de la factura")
    private Double tax;

    @Schema(description = "Total de la línea de la factura")
    private Double total;
}

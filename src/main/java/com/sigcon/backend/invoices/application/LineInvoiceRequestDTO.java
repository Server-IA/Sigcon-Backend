package com.sigcon.backend.invoices.application;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class LineInvoiceRequestDTO {

    @Schema (description = "ID del item", example = "1")
    private Long itemId;

    @Schema (description = "Cantidad", example = "1")
    private Double quantity;

    @Schema (description = "Precio", example = "100000")
    private Double price;

    @Schema (description = "Reglas tributarias", example = "10000")
    private List<LineInvoiceRulerTaxRequestDTO> taxRulesIds;
}

package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AAEF v1.0 - Summary de totales del lote (RF-INT-13 seccion "summary").
 *
 * <p>El receptor usa estos totales para validar que {@code TotalDocuments} coincida
 * con la suma real de documentos del lote (HU-INT-RF-02 escenario 3).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AaefSummaryDTO {

    @JsonProperty("TotalDocuments")
    private Integer totalDocuments;

    @JsonProperty("TotalInvoices")
    private Integer totalInvoices;

    @JsonProperty("TotalTransactions")
    private Integer totalTransactions;

    // Nota: el campo "TotalPayroll" del estandar AAEF original era parte de un
    // bloque desestimado del alcance. Se ignora si llega en el payload.

    @JsonProperty("TotalGrossAmount")
    private BigDecimal totalGrossAmount;

    @JsonProperty("TotalTaxes")
    private BigDecimal totalTaxes;

    @JsonProperty("TotalNet")
    private BigDecimal totalNet;

    /** Codigo ISO 4217 de la moneda. Ejemplo: "COP". */
    @JsonProperty("Currency")
    private String currency;
}

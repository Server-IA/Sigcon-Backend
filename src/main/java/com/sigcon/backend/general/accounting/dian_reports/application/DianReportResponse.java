package com.sigcon.backend.general.accounting.dian_reports.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de un reporte de Informacion Exogena DIAN generado.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DianReportResponse {

    /** Formato DIAN generado: F1001, F1007 o F1008. */
    private String format;

    /** Anio gravable del reporte. */
    private Integer year;

    /** Filas del reporte (una por tercero). */
    private List<DianReportRow> rows;

    /** Cantidad total de filas. */
    private Integer totalRows;

    /** Total agregado del reporte (suma de valor principal segun formato). */
    private BigDecimal totalAmount;
}

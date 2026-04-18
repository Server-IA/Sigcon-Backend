package com.sigcon.backend.general.accounting.dian_reports.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para generacion de un reporte de Informacion Exogena DIAN.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DianReportRequest {

    /** Formato DIAN: F1001, F1007 o F1008. */
    @NotBlank(message = "El formato es obligatorio")
    @Pattern(regexp = "F1001|F1007|F1008",
             message = "Formato invalido. Valores permitidos: F1001, F1007, F1008")
    private String format;

    /** Anio gravable (2000-2100). */
    @NotNull(message = "El anio es obligatorio")
    @Min(value = 2000, message = "Anio invalido")
    private Integer year;
}

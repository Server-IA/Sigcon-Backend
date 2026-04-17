package com.sigcon.backend.invoices.application;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para importacion masiva de facturas desde archivo CSV o XLSX.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkImportRequest {

    /** Contenido del archivo codificado en Base64. */
    @NotBlank(message = "El contenido del archivo es obligatorio")
    private String fileBase64;

    /** Delimitador del CSV (por defecto coma). Ignorado para XLSX. */
    @Builder.Default
    private String delimiter = ",";

    /** Formato del archivo: CSV o XLSX. */
    @Builder.Default
    private String format = "CSV";
}

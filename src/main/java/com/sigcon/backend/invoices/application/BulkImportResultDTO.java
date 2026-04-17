package com.sigcon.backend.invoices.application;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de resultado de la importacion masiva de facturas.
 * Incluye resumen de procesamiento y errores detallados por fila.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkImportResultDTO {

    /** Total de filas procesadas. */
    private int totalRows;

    /** Cantidad de facturas creadas exitosamente. */
    private int successCount;

    /** Cantidad de filas con errores. */
    private int errorCount;

    /** Detalle de errores por fila. */
    private List<RowError> errors;

    /**
     * Error individual de una fila durante la importacion.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RowError {
        /** Numero de fila (1-based). */
        private int row;
        /** Mensaje de error. */
        private String message;
    }
}

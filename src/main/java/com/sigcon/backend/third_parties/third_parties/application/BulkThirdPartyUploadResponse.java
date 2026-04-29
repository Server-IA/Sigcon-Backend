package com.sigcon.backend.third_parties.third_parties.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkThirdPartyUploadResponse {
    private int totalProcessed;
    private int created;
    private int updated;
    /**
     * HU-TER-07 E2/E3 (2026-04-27): lista de errores por fila para reporte
     * detallado. Antes una sola fila invalida abortaba todo el lote. Ahora
     * el procesamiento es tolerante: las filas validas se importan, las
     * invalidas se reportan aqui con motivo.
     */
    @lombok.Builder.Default
    private java.util.List<BulkRowError> errors = new java.util.ArrayList<>();
    private int failed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkRowError {
        private int line;
        private String nit;
        private String message;
    }
}
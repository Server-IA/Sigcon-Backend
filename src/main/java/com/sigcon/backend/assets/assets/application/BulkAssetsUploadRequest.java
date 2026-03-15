package com.sigcon.backend.assets.assets.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAssetsUploadRequest {

    @Schema(description = "Nombre del archivo origen", example = "activos.csv")
    private String fileName;

    @Schema(description = "Contenido del archivo en base64 (con o sin prefijo data:)",
            example = "data:text/csv;base64,UEsDB...")
    private String fileBase64;

    @Schema(description = "Delimitador para CSV", example = ",")
    private String delimiter;
}

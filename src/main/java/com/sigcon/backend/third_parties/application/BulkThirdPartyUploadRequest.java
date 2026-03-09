package com.sigcon.backend.third_parties.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkThirdPartyUploadRequest {
    @Schema(description = "Nombre del archivo origen", example = "terceros.csv")
    private String fileName;
    @Schema(description = "Contenido del archivo en base64 (con o sin prefijo data:)", example = "data:text/csv;base64,UEsDB...")
    private String fileBase64;
    @Schema(description = "Delimitador para CSV", example = ",")
    private String delimiter;
    @Schema(description = "Sobrescritura de NIT existente: S o N", example = "N")
    private String overwrite;
}

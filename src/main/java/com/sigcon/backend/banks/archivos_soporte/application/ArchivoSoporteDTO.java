package com.sigcon.backend.banks.archivos_soporte.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BNK-HU-062/063: metadatos de un soporte conservado (sin los bytes).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Soporte conservado (extracto/CSV/informe) con hash y retención")
public class ArchivoSoporteDTO {
    private Long id;
    private String tipo;
    private String fileName;
    private String mimeType;
    private String hashSha256;
    private Long fileSize;
    private Long bankAccountId;
    private Long reconciliationSessionId;
    private LocalDateTime uploadedAt;
    private LocalDateTime retenerHasta;
    private String replicationStatus;
}

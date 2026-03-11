package com.sigcon.backend.third_parties.ecl_segmentation.application;

import java.time.LocalDateTime;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.SegmentationSource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "EclSegmentationResponse", description = "Respuesta con el segmento de riesgo ECL vigente de un cliente (RF08)")
public class EclSegmentationResponse { 

    @Schema(description = "Identificador único del resultado de la segmentación", example = "1")
    private Long id; // Identificador unico del resultado de la segmentacion
    @Schema(description = "Identificador único del cliente", example = "1")
    private Long clientId; // Identificador unico del cliente
    @Schema(description = "Segmento de riesgo calculado automáticamente por el sistema segun reglas de mora", example = "LOW")
    private RiskSegmentation autoSegment; // Segmento de riesgo calculado automaticamente por el sistema
    @Schema(description = "Segmento de riesgo final vigente. Puede diferir del automático si fue ajustado manualmente", example = "MEDIUM")
    private RiskSegmentation finalSegment; // Segmento de riesgo final vigente (puede ser manual si fue ajustado) 
    @Schema(description = "Fuente de la segmentación vigente: AUTOMATIC (sistema) o MANUAL (analista o contador)", example = "AUTOMATIC")
    private SegmentationSource segmentationSource; // Fuente de la segmentacion (AUTOMATIC o MANUAL)
    @Schema(description = "Justificación del ajuste manual (solo aplica si segmentationSource es MANUAL)", example = "Cliente presenta mejora en su comportamiento de pago según reporte externo.")
    private String justification; // Justificacion del ajuste manual (si aplica) 
    @Schema(description = "Fecha en la que se realizó la segmentación o el último ajuste manual", example = "2026-03-10T10:00:00")
    private LocalDateTime calculationDate; // Fecha en la que se realizo la segmentacion o el ajuste manual
    @Schema(description = "Fecha de creación del registro", example = "2026-03-10T10:00:00")
    private LocalDateTime createdAt; // Fecha de creacion del registro 
    @Schema(description = "Fecha de la última actualización del registro", example = "2026-03-10T10:00:00")
    private LocalDateTime updatedAt; // Fecha de la ultima actualizacion del registro
}

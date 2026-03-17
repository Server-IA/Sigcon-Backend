package com.sigcon.backend.third_parties.ecl_segmentation.application;

import java.time.LocalDateTime;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.SegmentationSource;

import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "EclSegmentationHistoryResponse", description = "Respuesta con el registro histórico de un cambio de segmento de riesgo ECL de un cliente (RF08)")
public class EclSegmentationHistoryResponse {

    @Schema(description = "Identificador único del registro histórico", example = "1")
    private Long id; // Identificador unico del registro de segmentacion
    @Schema(description = "Identificador único del cliente", example = "1")
    private Long clientId; //Identificador unico (ID) del Cliente
    @Schema(description = "Información del tercero asociado al cliente", example = "{ \"id\": 1, \"thirdPartyCode\": \"TP001\", \"nit\": \"123456789\", \"dv\": \"5\", \"businessName\": \"Empresa Ejemplo S.A.\" }")
    private ThirdPartyDTO thirdParty; // Información del tercero asociado al cliente
    @Schema(description = "Segmento de riesgo anterior al cambio", example = "LOW")
    private RiskSegmentation previousSegment; // Segmento de riesgo anterior al cambio
    @Schema(description = "Segmento de riesgo nuevo asignado al cliente", example = "MEDIUM")
    private RiskSegmentation newSegment; // Segmento de riesgo nuevo asignado al cliente
    @Schema(description = "Fuente del cambio de segmento: AUTOMATIC (sistema) o MANUAL (analista o contador)", example = "AUTOMATIC")
    private SegmentationSource segmentationSource; // Fuente del cambio de segmento (AUTOMATIC o MANUAL)
    @Schema(description = "Justificación del cambio de segmento (obligatoria si el cambio fue manual)", example = "Cliente presenta mejora en su comportamiento de pago según reporte externo.")
    private String justification; // Justificacion del cambio de segmento (si aplica)
    @Schema(description = "Fecha en la que se realizó el cambio de segmento", example = "2026-03-10T10:00:00")
    private LocalDateTime changeDate; // Fecha en la que se realizo el cambio de segmento
}

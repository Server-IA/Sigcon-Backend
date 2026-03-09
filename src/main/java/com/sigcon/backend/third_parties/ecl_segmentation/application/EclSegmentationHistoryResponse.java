package com.sigcon.backend.third_parties.ecl_segmentation.application;

import java.time.LocalDateTime;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.SegmentationSource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EclSegmentationHistoryResponse {

    private Long id; // Identificador unico del registro de segmentacion
    private Long clientId; //Identificador unico (ID) del Cliente
    private RiskSegmentation previousSegment; // Segmento de riesgo anterior al cambio
    private RiskSegmentation newSegment; // Segmento de riesgo nuevo asignado al cliente
    private SegmentationSource segmentationSource; // Fuente del cambio de segmento (AUTOMATIC o MANUAL)
    private String justification; // Justificacion del cambio de segmento (si aplica)
    private LocalDateTime changeDate; // Fecha en la que se realizo el cambio de segmento
    
}

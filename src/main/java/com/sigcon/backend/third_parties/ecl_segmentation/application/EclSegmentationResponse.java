package com.sigcon.backend.third_parties.ecl_segmentation.application;

import java.time.LocalDateTime;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.SegmentationSource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EclSegmentationResponse { 

    private Long id; // Identificador unico del resultado de la segmentacion
    private Long clientId; // Identificador unico del cliente
    private RiskSegmentation autoSegment; // Segmento de riesgo calculado automaticamente por el sistema
    private RiskSegmentation finalSegment; // Segmento de riesgo final vigente (puede ser manual si fue ajustado) 
    private SegmentationSource segmentationSource; // Fuente de la segmentacion (AUTOMATIC o MANUAL)
    private String justification; // Justificacion del ajuste manual (si aplica) 
    private LocalDateTime calculationDate; // Fecha en la que se realizo la segmentacion o el ajuste manual
    private LocalDateTime createdAt; // Fecha de creacion del registro 
    private LocalDateTime updatedAt; // Fecha de la ultima actualizacion del registro

}

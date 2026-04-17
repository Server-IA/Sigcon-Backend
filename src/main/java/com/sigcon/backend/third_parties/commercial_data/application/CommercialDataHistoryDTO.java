package com.sigcon.backend.third_parties.commercial_data.application;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para registros del historial de cambios de datos comerciales.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommercialDataHistoryDTO {

    private Long id;
    private Long commercialDataId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Long changedBy;
    private LocalDateTime changedAt;
}

package com.sigcon.backend.third_parties.change_history.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de respuesta para el historial de cambios de un tercero.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChangeHistoryDTO {

    private Long id;
    private Long thirdPartyId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Long changedBy;
    private LocalDateTime changedAt;
}

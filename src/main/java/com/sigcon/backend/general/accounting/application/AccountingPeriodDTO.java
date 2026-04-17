package com.sigcon.backend.general.accounting.application;

import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO de lectura para periodo contable.
 * Se usa en las respuestas de la API para evitar exponer la entidad directamente.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountingPeriodDTO {
    private Long id;
    private Integer year;
    private Integer month;
    private String status;
    private LocalDateTime closedAt;
    private String closedBy;
    private LocalDateTime lockedAt;
    private String lockedBy;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

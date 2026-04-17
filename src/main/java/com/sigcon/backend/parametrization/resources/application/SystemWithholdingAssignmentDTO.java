package com.sigcon.backend.parametrization.resources.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para representar una asignacion de retencion del sistema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemWithholdingAssignmentDTO {

    private Long id;
    private Long withholdingId;
    private String withholdingName;
    private String withholdingCode;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
}

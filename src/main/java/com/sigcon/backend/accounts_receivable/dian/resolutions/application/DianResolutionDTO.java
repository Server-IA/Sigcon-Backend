package com.sigcon.backend.accounts_receivable.dian.resolutions.application;

import java.time.LocalDate;

import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model.DianResolutionStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO con la informacion completa de una resolucion DIAN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DianResolutionDTO {
    private Long id;
    private String resolutionNumber;
    private String prefix;
    private Long startNumber;
    private Long endNumber;
    private Long currentNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private String technicalKey;
    private DianResolutionStatus status;
    private String notes;
    /** Porcentaje usado del rango (0.0 a 100.0). */
    private Double usagePercent;
    /** Dias restantes de vigencia (puede ser negativo si expiro). */
    private Long daysToExpire;
    /** True si queda menos del 5% de numeracion disponible. */
    private Boolean rangeAlert;
    /** True si faltan menos de 30 dias para expirar. */
    private Boolean expirationAlert;
}

package com.sigcon.backend.assets.niif_alerts.application;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de lectura para revisiones anuales de activos (HU-ACT-12).
 * Incluye datos del activo asociado para facilitar presentacion en frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnualReviewDTO {

    private Long id;
    private Long assetId;
    private String assetCode;
    private String assetName;
    private LocalDate reviewDate;
    private Integer fiscalYear;
    private Integer previousUsefulLife;
    private Integer newUsefulLife;
    private BigDecimal previousResidualValue;
    private BigDecimal newResidualValue;
    private BigDecimal previousDepreciationMonthly;
    private BigDecimal newDepreciationMonthly;
    private String reviewType;
    private String justification;
    private Long reviewedBy;
    private LocalDateTime createdAt;
}

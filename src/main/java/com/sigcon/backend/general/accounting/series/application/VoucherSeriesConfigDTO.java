package com.sigcon.backend.general.accounting.series.application;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta y request para gestion de VoucherSeriesConfig.
 * Incluye campos calculados {@code usedPct} y {@code alert} para que la UI
 * muestre barras de progreso y badges sin tener que calcular.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoucherSeriesConfigDTO {
    private Long id;
    private String voucherType;
    private String prefix;
    private Long startNumber;
    private Long endNumber;
    private Long currentNumber;
    private Integer alertThresholdPct;
    private String description;
    private String status;

    /** Porcentaje del rango usado: ((current - start + 1) / (end - start + 1)) * 100. */
    private Integer usedPct;
    /** TRUE cuando usedPct >= alertThresholdPct — la UI lo pinta en rojo/amarillo. */
    private Boolean alert;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

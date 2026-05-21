package com.sigcon.backend.banks.matching.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** BNK-HU-072: request para crear/editar parámetros de matching. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Parámetros del motor de matching")
public class ParametrosMatchingRequest {
    /** NULL = parámetros globales de la empresa. */
    private Long cuentaBancariaId;
    private BigDecimal toleranciaMontoAbs;
    private BigDecimal toleranciaMontoPct;
    private Integer toleranciaFechaDias;
    private Integer umbralScoreAutoAprobar;
    private Integer umbralScoreSugerir;
    private Boolean permitirNaM;
    private Integer pesoMonto;
    private Integer pesoFecha;
    private Integer pesoTexto;
    private Integer pesoReferencia;
}

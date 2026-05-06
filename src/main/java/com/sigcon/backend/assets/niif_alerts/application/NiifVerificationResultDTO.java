package com.sigcon.backend.assets.niif_alerts.application;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiifVerificationResultDTO {

    private Long assetId;

    private String assetCode;

    private String assetName;

    private String result;

    private List<String> alerts;

    /**
     * HU-ACT-09 E1+E2 (QA 2026-05-05): detalle por criterio para que el contador
     * vea exactamente cuales cumplen y cuales no, en lugar de un mensaje agregado.
     */
    private List<CriterionResult> criteria;

    /**
     * HU-ACT-09 E1 (QA-BLOQUE-AY 2026-05-05): normas NIIF aplicables resueltas
     * por los criterios evaluados (ej. "NIC 16 §50, NIC 36, NIC 38 §69"). Antes
     * la columna del listado mostraba "-" porque el DTO no exponia este campo.
     */
    private String applicableNorm;

    /** Total de criterios evaluados en la verificacion. */
    private Integer totalCriteria;

    /** Numero de criterios marcados como CUMPLE. */
    private Integer compliantCount;

    /** Numero de criterios marcados como NO_CUMPLE. */
    private Integer nonCompliantCount;

    /** Numero de criterios marcados como ADVERTENCIA. */
    private Integer warningCount;

    /** Fecha y hora ISO-8601 de la verificacion (alimenta columna Fecha). */
    private String verifiedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionResult {
        /** Identificador corto del criterio (UTIL_LIFE, BOOK_VALUE, etc.). */
        private String code;
        /** Nombre legible del criterio. */
        private String name;
        /** CUMPLE | ADVERTENCIA | NO_CUMPLE. */
        private String status;
        /** Mensaje opcional cuando no cumple o tiene advertencia. */
        private String message;
    }
}
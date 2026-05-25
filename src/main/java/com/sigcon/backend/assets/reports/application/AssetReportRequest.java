package com.sigcon.backend.assets.reports.application;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de solicitud para la generacion de reportes de activos fijos.
 *
 * <p>Permite filtrar activos por rango de fechas de adquisicion y opcionalmente
 * agrupar los resultados por clasificacion, periodo mensual o sin agrupamiento.</p>
 *
 * @see com.sigcon.backend.assets.reports.domain.service.AssetReportService
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssetReportRequest {

    /** Fecha de inicio del rango de adquisicion (inclusive). */
    @NotNull(message = "La fecha de inicio es obligatoria.")
    private LocalDate startDate;

    /** Fecha de fin del rango de adquisicion (inclusive). */
    @NotNull(message = "La fecha de fin es obligatoria.")
    private LocalDate endDate;

    /**
     * Criterio de agrupamiento para el reporte.
     * <ul>
     *   <li><b>classification</b>: agrupa por clasificacion del activo (CURRENT / NON_CURRENT).</li>
     *   <li><b>period</b>: agrupa por mes de adquisicion.</li>
     *   <li><b>asset</b> (o null): sin agrupamiento, lista todos los activos.</li>
     * </ul>
     */
    @Builder.Default
    private String groupBy = "asset";

    // ─── QA Activos (2026-05-25) Error 03: filtros adicionales ───────────────
    // Antes el reporte SOLO filtraba por rango de fechas; el resto de filtros
    // que enviaba el frontend se descartaban. Estos campos son opcionales: si
    // llegan null no se aplican.

    /** Filtra por proveedor (ThirdParty.id). */
    private Long supplierId;

    /** Filtra por codigo PUC de la cuenta contable (prefijo, ej. "1516"). */
    private String classificationCode;

    /** Filtra por estado del activo (ACTIVE / IN_REPAIR / DECOMMISSIONED / TRANSFERRED). */
    private String status;

    /** Filtra por tipo de activo (TANGIBLE / INTANGIBLE). */
    private String assetType;
}

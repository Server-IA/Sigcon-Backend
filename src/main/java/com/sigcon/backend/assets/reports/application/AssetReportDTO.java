package com.sigcon.backend.assets.reports.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta que representa un activo dentro del reporte generado.
 *
 * <p>Contiene la informacion resumida del activo fijo necesaria para su presentacion
 * en reportes de inventario, depreciacion o clasificacion.</p>
 *
 * @see com.sigcon.backend.assets.reports.domain.service.AssetReportService
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssetReportDTO {

    /** Codigo unico del activo. */
    private String assetCode;

    /** Nombre descriptivo del activo. */
    private String assetName;

    /** Clasificacion contable (CURRENT / NON_CURRENT). */
    private String classification;

    /** Fecha de adquisicion del activo. */
    private LocalDate acquisitionDate;

    /** Valor de adquisicion original. */
    private BigDecimal acquisitionValue;

    /** Valor actual en libros despues de depreciaciones. */
    private BigDecimal currentBookValue;

    /** Depreciacion acumulada (adquisicion - valor en libros). */
    private BigDecimal depreciation;

    /** Estado actual del activo (ACTIVE, IN_REPAIR, DECOMMISSIONED, TRANSFERRED). */
    private String status;

    /** Nombre o razon social del proveedor. */
    private String supplierName;
}

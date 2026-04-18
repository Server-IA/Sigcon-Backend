package com.sigcon.backend.general.accounting.statements.application;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para el Estado de Resultados Integral.
 * Estructura conforme a NIC 1 y PUC colombiano:
 * Clases 4 (Ingresos), 5 (Gastos), 6 (Costos de venta), 7 (Costos de produccion).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstadoResultadosDTO {

    /** Total de ingresos (clase 4 PUC). */
    private BigDecimal totalIngresos;

    /** Total de gastos (clase 5 PUC). */
    private BigDecimal totalGastos;

    /** Total de costos de venta + produccion (clases 6 y 7 PUC). */
    private BigDecimal totalCostos;

    /** Utilidad bruta: ingresos - costos. */
    private BigDecimal utilidadBruta;

    /** Utilidad (o perdida) neta: ingresos - gastos - costos. */
    private BigDecimal utilidadNeta;

    /** Detalle por clase contable con desglose por cuenta. */
    private List<BalanceGeneralDTO.ClassDetailDTO> details;
}

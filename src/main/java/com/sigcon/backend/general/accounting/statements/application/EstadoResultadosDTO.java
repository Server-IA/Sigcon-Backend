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

    // ───────────────────────────────────────────────────────────────
    // HU-CG-10: clasificacion financiera granular NIC 1.
    // Derivada del subgrupo PUC (Decreto 2650/1993) de cada cuenta:
    //  - 41   Ingresos operacionales
    //  - 4210 Ingresos financieros        (subconjunto de 42 no operacionales)
    //  - 42   Otros ingresos (no operacionales, excepto 4210)
    //  - 51   Gastos de administracion
    //  - 52   Gastos de ventas
    //  - 5305 Gastos financieros          (subconjunto de 53 no operacionales)
    //  - 53   Otros gastos (no operacionales, excepto 5305)
    //  - 54   Impuesto de renta y complementarios
    // Campos ADITIVOS: los totales y utilidades anteriores conservan su semantica.
    // ───────────────────────────────────────────────────────────────

    /** Ingresos operacionales (PUC 41). */
    private BigDecimal ingresosOperacionales;
    /** Ingresos financieros (PUC 4210). */
    private BigDecimal ingresosFinancieros;
    /** Otros ingresos no operacionales (PUC 42 excepto 4210, y 43-49). */
    private BigDecimal otrosIngresos;

    /** Gastos de administracion (PUC 51). */
    private BigDecimal gastosAdministracion;
    /** Gastos de ventas (PUC 52). */
    private BigDecimal gastosVentas;
    /** Gastos financieros (PUC 5305). */
    private BigDecimal gastosFinancieros;
    /** Otros gastos no operacionales (PUC 53 excepto 5305). */
    private BigDecimal otrosGastos;
    /** Gasto por impuesto de renta y complementarios (PUC 54). */
    private BigDecimal impuestoRenta;

    /**
     * Utilidad bruta operacional NIC 1: ingresos operacionales - costos.
     * (A diferencia de utilidadBruta, que usa el total de ingresos incluyendo no
     * operacionales; este campo es el correcto para la presentacion por funcion.)
     */
    private BigDecimal utilidadBrutaOperacional;

    /** Utilidad operacional: (ingresos operacionales - costos) - gastos admin - gastos ventas. */
    private BigDecimal utilidadOperacional;
    /** Utilidad antes de impuestos: utilidad operacional + ingresos no oper. - gastos no oper. */
    private BigDecimal utilidadAntesImpuestos;

    /** Detalle por clase contable con desglose por cuenta. */
    private List<BalanceGeneralDTO.ClassDetailDTO> details;
}

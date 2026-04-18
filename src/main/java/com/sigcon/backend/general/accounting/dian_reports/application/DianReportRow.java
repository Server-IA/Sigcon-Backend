package com.sigcon.backend.general.accounting.dian_reports.application;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fila generica para los formatos de Informacion Exogena DIAN
 * (F1001, F1007, F1008). No todos los campos aplican a todos los formatos:
 * los campos no utilizados se omiten en el JSON mediante {@code @JsonInclude}.
 *
 * <p>La Informacion Exogena es el reporte anual que los contribuyentes
 * deben entregar a la DIAN segun la Resolucion vigente (actualmente
 * Resolucion 000162 de 2023 y modificatorias) con el detalle de pagos,
 * ingresos, retenciones y saldos.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DianReportRow {

    /** Tipo de documento del tercero (NIT, CC, CE, etc.). */
    private String tipoDocumento;

    /** Numero de documento / NIT del tercero. */
    private String numeroDocumento;

    /** Digito de verificacion (cuando aplica). */
    private String dv;

    /** Nombres o razon social del tercero. */
    private String nombresORazonSocial;

    /** Concepto DIAN (codigo segun Resolucion, p.ej. 5001, 5002). */
    private String concepto;

    // ───────────────── F1001 ─────────────────

    /** F1001: pago o abono en cuenta realizado en el anio. */
    private BigDecimal pagoOAbono;

    /** F1001: retencion en la fuente practicada. */
    private BigDecimal retencionEnLaFuente;

    /** F1001: IVA descontable asociado. */
    private BigDecimal ivaDescontable;

    // ───────────────── F1007 ─────────────────

    /** F1007: ingreso bruto operacional recibido en el anio. */
    private BigDecimal ingresoBrutoOperacional;

    /** F1007: devoluciones, rebajas y descuentos otorgados. */
    private BigDecimal devolucionesRebajasDescuentos;

    /** F1007: ingresos no constitutivos de renta. */
    private BigDecimal ingresoNoConstitutivo;

    // ───────────────── F1008 ─────────────────

    /** F1008: saldo de cuentas por cobrar al 31 de diciembre. */
    private BigDecimal saldoCuentasPorCobrar;
}

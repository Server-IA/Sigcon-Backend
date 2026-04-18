package com.sigcon.backend.general.accounting.tax_reports.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO del reporte de cuadre de IVA bimestral (HU-CG-32).
 * <p>Sirve como insumo para el Formulario 300 de la DIAN y muestra
 * el IVA generado (a cargo), el descontable (a favor) y el saldo
 * resultante del bimestre.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvaReportDTO {

    /** Anio gravable del reporte. */
    private Integer year;

    /** Numero del bimestre (1 a 6). */
    private Integer bimester;

    /** Etiqueta legible del bimestre (ej. "Enero-Febrero"). */
    private String bimesterLabel;

    /** IVA generado en ventas (sumatoria de totalTax de sales_invoices). */
    private BigDecimal ivaGenerado;

    /** IVA descontable en compras (sumatoria de total_tax de invoices AP). */
    private BigDecimal ivaDescontable;

    /** Saldo de IVA: generado - descontable. */
    private BigDecimal saldoIva;

    /** "A pagar" si saldo >= 0, "A favor" en caso contrario. */
    private String saldoTipo;

    /** Cantidad de facturas de venta del bimestre. */
    private Integer countFacturasVenta;

    /** Cantidad de facturas de compra del bimestre. */
    private Integer countFacturasCompra;
}

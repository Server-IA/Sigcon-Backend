package com.sigcon.backend.general.accounting.dian_reports.domain.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sigcon.backend.general.accounting.dian_reports.application.DianReportResponse;
import com.sigcon.backend.general.accounting.dian_reports.application.DianReportRow;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para la generacion de reportes de Informacion Exogena DIAN.
 *
 * <p>La Informacion Exogena es el reporte anual tributario exigido por la
 * Direccion de Impuestos y Aduanas Nacionales (DIAN) mediante resolucion
 * anual (actualmente Resolucion 000162 de 2023). Consiste en un conjunto
 * de formatos con la relacion detallada, por tercero, de los pagos,
 * ingresos, retenciones y saldos del contribuyente durante el anio
 * gravable.</p>
 *
 * <p>Este servicio implementa tres de los formatos mas comunes:</p>
 * <ul>
 *   <li><b>F1001</b>: Pagos o abonos en cuenta y retenciones practicadas
 *       a proveedores (construido desde AP: facturas de compra y pagos).</li>
 *   <li><b>F1007</b>: Ingresos recibidos en el anio
 *       (construido desde AR: facturas de venta y notas credito).</li>
 *   <li><b>F1008</b>: Saldos de cuentas por cobrar al 31 de diciembre
 *       (construido desde AR: facturas de venta con saldo pendiente).</li>
 * </ul>
 *
 * <p>Los codigos de concepto se simplifican (5001 = servicios, 5002 =
 * bienes). En un entorno de produccion se mapearia por cuenta PUC segun
 * la tabla de conceptos vigente de la DIAN.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DianReportService {

    @PersistenceContext
    private EntityManager em;

    // ────────────────────────────────────────────────────────────
    // F1001 - Pagos y retenciones
    // ────────────────────────────────────────────────────────────

    /**
     * Genera el Formato 1001 con pagos, retenciones e IVA descontable
     * agrupados por tercero proveedor.
     *
     * @param year anio gravable
     * @return reporte DIAN F1001
     */
    @SuppressWarnings("unchecked")
    public DianReportResponse generateF1001(int year) {
        log.info("Generando F1001 para anio {}", year);

        // Agrega ap_payments + invoices por tercero. Usamos SQL nativo
        // para garantizar coherencia con las columnas reales de BD.
        String sql = "SELECT t.id, t.nit, t.dv, t.business_name, "
                   + "  COALESCE(SUM(p.amount),0)                 AS pago_abono, "
                   + "  COALESCE(SUM(DISTINCT i.total_tax),0)      AS iva_descontable, "
                   + "  COALESCE(SUM(DISTINCT (i.total_amount - i.total_payment - i.total_tax + i.total_discount)),0) AS retencion_estimada "
                   + "FROM ap_payments p "
                   + "JOIN invoices i ON i.id = p.invoice_id AND i.deleted_at IS NULL "
                   + "JOIN third_parties t ON t.id = i.third_party_id AND t.deleted_at IS NULL "
                   + "WHERE p.deleted_at IS NULL "
                   + "  AND EXTRACT(YEAR FROM p.payment_date) = :year "
                   + "GROUP BY t.id, t.nit, t.dv, t.business_name "
                   + "ORDER BY t.business_name";

        List<Object[]> records = em.createNativeQuery(sql)
                .setParameter("year", year)
                .getResultList();

        List<DianReportRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] r : records) {
            BigDecimal pago   = toBd(r[4]);
            BigDecimal iva    = toBd(r[5]);
            BigDecimal reten  = toBd(r[6]);
            if (reten.signum() < 0) reten = BigDecimal.ZERO;

            rows.add(DianReportRow.builder()
                    .tipoDocumento("NIT")
                    .numeroDocumento(asString(r[1]))
                    .dv(asString(r[2]))
                    .nombresORazonSocial(asString(r[3]))
                    .concepto("5001")
                    .pagoOAbono(pago)
                    .retencionEnLaFuente(reten)
                    .ivaDescontable(iva)
                    .build());
            total = total.add(pago);
        }

        return DianReportResponse.builder()
                .format("F1001")
                .year(year)
                .rows(rows)
                .totalRows(rows.size())
                .totalAmount(total)
                .build();
    }

    // ────────────────────────────────────────────────────────────
    // F1007 - Ingresos recibidos
    // ────────────────────────────────────────────────────────────

    /**
     * Genera el Formato 1007 con los ingresos brutos operacionales
     * recibidos en el anio, agrupados por cliente.
     *
     * <p>Descuenta notas credito (type=CREDIT) del mismo anio.</p>
     *
     * @param year anio gravable
     * @return reporte DIAN F1007
     */
    @SuppressWarnings("unchecked")
    public DianReportResponse generateF1007(int year) {
        log.info("Generando F1007 para anio {}", year);

        String sql = "SELECT t.id, t.nit, t.dv, t.business_name, "
                   + "  COALESCE(SUM(s.subtotal),0) AS ingreso_bruto, "
                   + "  COALESCE(( "
                   + "     SELECT SUM(n.amount) FROM ar_credit_debit_notes n "
                   + "     JOIN sales_invoices si ON si.id = n.invoice_id "
                   + "     WHERE n.note_type='CREDIT' AND n.deleted_at IS NULL "
                   + "       AND si.third_party_id = t.id "
                   + "       AND EXTRACT(YEAR FROM si.invoice_date) = :year "
                   + "  ),0) AS devoluciones "
                   + "FROM sales_invoices s "
                   + "JOIN third_parties t ON t.id = s.third_party_id AND t.deleted_at IS NULL "
                   + "WHERE s.deleted_at IS NULL "
                   + "  AND s.status <> 'VOIDED' "
                   + "  AND EXTRACT(YEAR FROM s.invoice_date) = :year "
                   + "GROUP BY t.id, t.nit, t.dv, t.business_name "
                   + "ORDER BY t.business_name";

        List<Object[]> records = em.createNativeQuery(sql)
                .setParameter("year", year)
                .getResultList();

        List<DianReportRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] r : records) {
            BigDecimal ingreso       = toBd(r[4]);
            BigDecimal devoluciones  = toBd(r[5]);

            rows.add(DianReportRow.builder()
                    .tipoDocumento("NIT")
                    .numeroDocumento(asString(r[1]))
                    .dv(asString(r[2]))
                    .nombresORazonSocial(asString(r[3]))
                    .concepto("4001")
                    .ingresoBrutoOperacional(ingreso)
                    .devolucionesRebajasDescuentos(devoluciones)
                    .ingresoNoConstitutivo(BigDecimal.ZERO)
                    .build());
            total = total.add(ingreso);
        }

        return DianReportResponse.builder()
                .format("F1007")
                .year(year)
                .rows(rows)
                .totalRows(rows.size())
                .totalAmount(total)
                .build();
    }

    // ────────────────────────────────────────────────────────────
    // F1008 - Saldos de Cuentas por Cobrar al 31-dic
    // ────────────────────────────────────────────────────────────

    /**
     * Genera el Formato 1008 con los saldos de cuentas por cobrar
     * a 31 de diciembre del anio, por cliente.
     *
     * @param year anio gravable
     * @return reporte DIAN F1008
     */
    @SuppressWarnings("unchecked")
    public DianReportResponse generateF1008(int year) {
        log.info("Generando F1008 para anio {}", year);

        String sql = "SELECT t.id, t.nit, t.dv, t.business_name, "
                   + "  COALESCE(SUM(s.balance_due),0) AS saldo_cxc "
                   + "FROM sales_invoices s "
                   + "JOIN third_parties t ON t.id = s.third_party_id AND t.deleted_at IS NULL "
                   + "WHERE s.deleted_at IS NULL "
                   + "  AND s.balance_due > 0 "
                   + "  AND s.status IN ('ISSUED','PARTIALLY_PAID','OVERDUE') "
                   + "  AND EXTRACT(YEAR FROM s.invoice_date) <= :year "
                   + "GROUP BY t.id, t.nit, t.dv, t.business_name "
                   + "HAVING COALESCE(SUM(s.balance_due),0) > 0 "
                   + "ORDER BY t.business_name";

        List<Object[]> records = em.createNativeQuery(sql)
                .setParameter("year", year)
                .getResultList();

        List<DianReportRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] r : records) {
            BigDecimal saldo = toBd(r[4]);
            rows.add(DianReportRow.builder()
                    .tipoDocumento("NIT")
                    .numeroDocumento(asString(r[1]))
                    .dv(asString(r[2]))
                    .nombresORazonSocial(asString(r[3]))
                    .concepto("1315")
                    .saldoCuentasPorCobrar(saldo)
                    .build());
            total = total.add(saldo);
        }

        return DianReportResponse.builder()
                .format("F1008")
                .year(year)
                .rows(rows)
                .totalRows(rows.size())
                .totalAmount(total)
                .build();
    }

    // ────────────────────────────────────────────────────────────
    // Exportacion CSV
    // ────────────────────────────────────────────────────────────

    /**
     * Exporta la respuesta DIAN a un archivo CSV compatible con Excel
     * en espanol (BOM UTF-8 + separador coma).
     *
     * @param response respuesta generada previamente
     * @return bytes del archivo CSV
     */
    public byte[] exportToCsv(DianReportResponse response) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // BOM UTF-8 para que Excel reconozca acentos correctamente
        out.write(0xEF); out.write(0xBB); out.write(0xBF);

        StringBuilder sb = new StringBuilder();
        String format = response.getFormat();

        // Encabezados segun formato
        switch (format) {
            case "F1001":
                sb.append("Concepto,TipoDocumento,NumeroDocumento,DV,RazonSocial,PagoOAbono,RetencionEnLaFuente,IVADescontable\n");
                for (DianReportRow row : response.getRows()) {
                    sb.append(safe(row.getConcepto())).append(',')
                      .append(safe(row.getTipoDocumento())).append(',')
                      .append(safe(row.getNumeroDocumento())).append(',')
                      .append(safe(row.getDv())).append(',')
                      .append(safe(row.getNombresORazonSocial())).append(',')
                      .append(num(row.getPagoOAbono())).append(',')
                      .append(num(row.getRetencionEnLaFuente())).append(',')
                      .append(num(row.getIvaDescontable())).append('\n');
                }
                break;
            case "F1007":
                sb.append("Concepto,TipoDocumento,NumeroDocumento,DV,RazonSocial,IngresoBrutoOperacional,DevolucionesRebajasDescuentos,IngresoNoConstitutivo\n");
                for (DianReportRow row : response.getRows()) {
                    sb.append(safe(row.getConcepto())).append(',')
                      .append(safe(row.getTipoDocumento())).append(',')
                      .append(safe(row.getNumeroDocumento())).append(',')
                      .append(safe(row.getDv())).append(',')
                      .append(safe(row.getNombresORazonSocial())).append(',')
                      .append(num(row.getIngresoBrutoOperacional())).append(',')
                      .append(num(row.getDevolucionesRebajasDescuentos())).append(',')
                      .append(num(row.getIngresoNoConstitutivo())).append('\n');
                }
                break;
            case "F1008":
                sb.append("Concepto,TipoDocumento,NumeroDocumento,DV,RazonSocial,SaldoCuentasPorCobrar\n");
                for (DianReportRow row : response.getRows()) {
                    sb.append(safe(row.getConcepto())).append(',')
                      .append(safe(row.getTipoDocumento())).append(',')
                      .append(safe(row.getNumeroDocumento())).append(',')
                      .append(safe(row.getDv())).append(',')
                      .append(safe(row.getNombresORazonSocial())).append(',')
                      .append(num(row.getSaldoCuentasPorCobrar())).append('\n');
                }
                break;
            default:
                throw new IllegalArgumentException("Formato no soportado: " + format);
        }

        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        try {
            out.write(content);
        } catch (Exception e) {
            throw new IllegalStateException("Error al generar CSV: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    // ────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────

    private String safe(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"");
        // Si contiene coma o comilla, envolver en comillas dobles
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String num(BigDecimal n) {
        return n == null ? "0" : n.toPlainString();
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        return new BigDecimal(v.toString());
    }

    private String asString(Object v) {
        return v == null ? "" : v.toString();
    }
}

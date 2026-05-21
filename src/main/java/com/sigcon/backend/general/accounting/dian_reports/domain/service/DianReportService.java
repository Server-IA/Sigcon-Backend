package com.sigcon.backend.general.accounting.dian_reports.domain.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.general.accounting.dian_reports.application.DianReportResponse;
import com.sigcon.backend.general.accounting.dian_reports.application.DianReportRow;
import com.sigcon.backend.utils.export.ReportContextResolver;
import com.sigcon.backend.utils.export.ReportHeaderBuilder;

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

    /**
     * Resolver de contexto de reporte (empresa+usuario+filtros). Opcional para
     * conservar compatibilidad con tests que instancien la clase sin Spring.
     */
    @Autowired(required = false)
    private ReportContextResolver reportContextResolver;

    /**
     * Audit publisher opcional. Si esta presente, cada CSV/PDF se registra
     * como EVENT EXPORT (HU-CG-19 E5).
     */
    @Autowired(required = false)
    private AuditPublisher auditPublisher;

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

        publishGenerateAudit("F1001", year, rows.size());
        return DianReportResponse.builder()
                .format("F1001")
                .year(year)
                .rows(rows)
                .totalRows(rows.size())
                .totalAmount(total)
                .build();
    }

    private void publishGenerateAudit(String format, int year, int rows) {
        if (auditPublisher == null) return;
        try {
            auditPublisher.publish(AuditAction.VIEW, AuditModule.CG, AuditSeverity.LOW,
                    "DianReport", null,
                    "Generacion DIAN " + format + " " + year + " filas=" + rows,
                    null, null, null);
        } catch (RuntimeException ignored) { /* audit no debe romper */ }
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

        publishGenerateAudit("F1007", year, rows.size());
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

        publishGenerateAudit("F1008", year, rows.size());
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
        // HU-CG-19 E5: registrar EXPORT en auditoria
        if (auditPublisher != null) {
            try {
                auditPublisher.publish(AuditAction.EXPORT, AuditModule.CG, AuditSeverity.LOW,
                        "DianReport", null,
                        "Export DIAN " + response.getFormat() + " " + response.getYear()
                                + " formato=CSV filas=" + response.getTotalRows(),
                        null, null, null);
            } catch (RuntimeException ignored) { /* no romper export */ }
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

    // ────────────────────────────────────────────────────────────
    // HU-CG-19 E4: Exportacion PDF
    // ────────────────────────────────────────────────────────────

    /**
     * Genera un PDF formal del reporte DIAN con encabezado de empresa y
     * tabla. Util para presentar al revisor fiscal junto con el CSV oficial.
     */
    public byte[] exportToPdf(DianReportResponse response) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4.rotate());
            try (Document doc = new Document(pdf)) {
                PdfFont titleFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                PdfFont bodyFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                // Resolver contexto
                ReportHeaderBuilder.ReportContext ctx = null;
                if (reportContextResolver != null) {
                    ReportHeaderBuilder.ReportContext.Builder b = reportContextResolver
                            .baseContext("Reporte DIAN " + response.getFormat()
                                    + " - Anio gravable " + response.getYear());
                    b.addFilter("Formato", response.getFormat());
                    b.addFilter("Anio gravable", String.valueOf(response.getYear()));
                    b.addTotal("Registros", BigDecimal.valueOf(response.getTotalRows()));
                    if (response.getTotalAmount() != null) {
                        b.addTotal("Total monto", response.getTotalAmount());
                    }
                    ctx = b.build();
                }

                doc.add(new Paragraph("DIAN - Informacion Exogena " + response.getFormat())
                        .setFont(titleFont).setFontSize(16)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(new DeviceRgb(30, 58, 138)));

                if (ctx != null) {
                    StringBuilder meta = new StringBuilder();
                    if (ctx.companyName != null) {
                        meta.append("Empresa: ").append(ctx.companyName);
                        if (ctx.companyNit != null) meta.append(" - NIT ").append(ctx.companyNit);
                        meta.append('\n');
                    }
                    if (ctx.userEmail != null) {
                        meta.append("Generado por: ").append(ctx.userEmail);
                        if (ctx.roles != null && !ctx.roles.isEmpty()) {
                            meta.append(" (").append(String.join(", ", ctx.roles)).append(")");
                        }
                        meta.append('\n');
                    }
                    meta.append("Generado: ")
                            .append(LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    doc.add(new Paragraph(meta.toString())
                            .setFont(bodyFont).setFontSize(9)
                            .setFontColor(new DeviceRgb(75, 85, 99)));
                }

                // Tabla
                String format = response.getFormat();
                String[] headers;
                switch (format) {
                    case "F1001":
                        headers = new String[]{ "Concepto", "Tipo Doc", "NIT", "DV",
                                "Razon Social", "Pago/Abono", "Retencion", "IVA Descontable" };
                        break;
                    case "F1007":
                        headers = new String[]{ "Concepto", "Tipo Doc", "NIT", "DV",
                                "Razon Social", "Ingreso Bruto",
                                "Devoluciones", "Ing. No Constitutivo" };
                        break;
                    case "F1008":
                        headers = new String[]{ "Concepto", "Tipo Doc", "NIT", "DV",
                                "Razon Social", "Saldo CxC" };
                        break;
                    default:
                        throw new IllegalArgumentException("Formato no soportado: " + format);
                }
                Table table = new Table(UnitValue.createPercentArray(headers.length))
                        .useAllAvailableWidth();
                for (String h : headers) {
                    table.addHeaderCell(new Cell().add(new Paragraph(h)
                            .setFont(titleFont).setFontSize(9))
                            .setBackgroundColor(new DeviceRgb(238, 242, 255)));
                }

                DecimalFormat df = new DecimalFormat("#,##0.00",
                        new DecimalFormatSymbols(new Locale("es", "CO")));
                for (DianReportRow row : response.getRows()) {
                    table.addCell(cell(row.getConcepto(), bodyFont));
                    table.addCell(cell(row.getTipoDocumento(), bodyFont));
                    table.addCell(cell(row.getNumeroDocumento(), bodyFont));
                    table.addCell(cell(row.getDv(), bodyFont));
                    table.addCell(cell(row.getNombresORazonSocial(), bodyFont));
                    switch (format) {
                        case "F1001":
                            table.addCell(cellNum(row.getPagoOAbono(), df, bodyFont));
                            table.addCell(cellNum(row.getRetencionEnLaFuente(), df, bodyFont));
                            table.addCell(cellNum(row.getIvaDescontable(), df, bodyFont));
                            break;
                        case "F1007":
                            table.addCell(cellNum(row.getIngresoBrutoOperacional(), df, bodyFont));
                            table.addCell(cellNum(row.getDevolucionesRebajasDescuentos(), df, bodyFont));
                            table.addCell(cellNum(row.getIngresoNoConstitutivo(), df, bodyFont));
                            break;
                        case "F1008":
                            table.addCell(cellNum(row.getSaldoCuentasPorCobrar(), df, bodyFont));
                            break;
                        default: break;
                    }
                }
                doc.add(table);

                // Resumen
                doc.add(new Paragraph(String.format(
                        "Total registros: %d  |  Total monto: $%s",
                        response.getTotalRows(),
                        response.getTotalAmount() != null
                                ? df.format(response.getTotalAmount())
                                : "0.00"))
                        .setFont(titleFont).setFontSize(10)
                        .setFontColor(new DeviceRgb(30, 58, 138)));

                doc.add(new Paragraph(
                        "Documento generado automaticamente. Pagina con valor probatorio "
                      + "para auditoria interna. Para presentacion oficial use el CSV.")
                        .setFont(bodyFont).setFontSize(7)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.GRAY));
            }
            // HU-CG-19 E5: registrar EXPORT en auditoria
            if (auditPublisher != null) {
                try {
                    auditPublisher.publish(AuditAction.EXPORT, AuditModule.CG, AuditSeverity.LOW,
                            "DianReport", null,
                            "Export DIAN " + response.getFormat() + " "
                                    + response.getYear() + " formato=PDF filas="
                                    + response.getTotalRows(),
                            null, null, null);
                } catch (RuntimeException ignored) { /* audit no debe romper PDF */ }
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando PDF DIAN: " + e.getMessage(), e);
        }
    }

    private Cell cell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text != null ? text : "")
                .setFont(font).setFontSize(8));
    }

    private Cell cellNum(BigDecimal value, DecimalFormat df, PdfFont font) {
        return new Cell().add(new Paragraph(value != null ? df.format(value) : "0.00")
                .setFont(font).setFontSize(8)
                .setTextAlignment(TextAlignment.RIGHT));
    }
}

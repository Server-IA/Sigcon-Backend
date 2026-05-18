package com.sigcon.backend.utils.export;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QA Bloque BJ - Formato estandar de exportaciones (2026-05-17).
 *
 * <p>Helper transversal que escribe el HEADER estandar exigido por el profesor
 * en TODA exportacion contable (PDF / XLSX / CSV). Antes cada reporte ponia un
 * formato distinto y el cliente no podia identificar empresa / usuario /
 * filtros / totales rapidamente.
 *
 * <p><b>Contenido del header estandar</b>:
 * <ul>
 *   <li>Nombre de la empresa + NIT</li>
 *   <li>Generado por: email del usuario + rol(es)</li>
 *   <li>Fecha y hora de generacion (yyyy-MM-dd HH:mm:ss)</li>
 *   <li>Filtros aplicados (si se proporcionan)</li>
 *   <li>Totales / saldos relevantes al reporte (si se proporcionan)</li>
 * </ul>
 *
 * <p>El formato modelo es el de {@code ApReportService} ("Estado de Cuenta
 * Proveedor") indicado por el lider del proyecto como referencia.
 *
 * <p>Uso tipico:
 * <pre>
 *   // 1) Construir contexto de header
 *   ReportContext ctx = ReportContext.builder()
 *       .companyName("ACME SAS").companyNit("900123456")
 *       .userEmail("contador@acme.test").roles(List.of("CONTADOR"))
 *       .reportTitle("Reporte de Facturas de Venta")
 *       .addFilter("Periodo", "2026-04-01 a 2026-04-30")
 *       .addFilter("Estado", "ISSUED, PARTIALLY_PAID")
 *       .addTotal("Total Facturado", new BigDecimal("12500000"))
 *       .addTotal("Saldo Pendiente", new BigDecimal("4500000"))
 *       .build();
 *
 *   // 2) Escribir el header en PDF / XLSX / CSV
 *   ReportHeaderBuilder.writePdfHeader(document, ctx);
 *   ReportHeaderBuilder.writeXlsxHeader(workbook, sheet, ctx, 8);
 *   String csvHeader = ReportHeaderBuilder.buildCsvHeader(ctx);
 * </pre>
 */
public final class ReportHeaderBuilder {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReportHeaderBuilder() {}

    /**
     * Contexto inmutable del header. Se construye con builder fluido.
     * Cualquier campo opcional null se omite del render (no aparece la fila).
     */
    public static final class ReportContext {
        public final String companyName;
        public final String companyNit;
        public final String userEmail;
        public final List<String> roles;
        public final String reportTitle;
        public final LocalDateTime generatedAt;
        public final Map<String, String> filters;
        public final Map<String, BigDecimal> totals;

        private ReportContext(Builder b) {
            this.companyName = b.companyName != null ? b.companyName : "(empresa no configurada)";
            this.companyNit = b.companyNit;
            this.userEmail = b.userEmail != null ? b.userEmail : "(sistema)";
            this.roles = b.roles != null ? b.roles : List.of();
            this.reportTitle = b.reportTitle != null ? b.reportTitle : "Reporte";
            this.generatedAt = b.generatedAt != null ? b.generatedAt : LocalDateTime.now();
            this.filters = b.filters;
            this.totals = b.totals;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String companyName;
            private String companyNit;
            private String userEmail;
            private List<String> roles;
            private String reportTitle;
            private LocalDateTime generatedAt;
            private Map<String, String> filters = new LinkedHashMap<>();
            private Map<String, BigDecimal> totals = new LinkedHashMap<>();

            public Builder companyName(String v) { this.companyName = v; return this; }
            public Builder companyNit(String v) { this.companyNit = v; return this; }
            public Builder userEmail(String v) { this.userEmail = v; return this; }
            public Builder roles(List<String> v) { this.roles = v; return this; }
            public Builder reportTitle(String v) { this.reportTitle = v; return this; }
            public Builder generatedAt(LocalDateTime v) { this.generatedAt = v; return this; }
            public Builder addFilter(String name, String value) {
                if (value != null && !value.isBlank()) this.filters.put(name, value);
                return this;
            }
            public Builder addTotal(String name, BigDecimal value) {
                if (value != null) this.totals.put(name, value);
                return this;
            }
            public ReportContext build() { return new ReportContext(this); }
        }
    }

    // ============= PDF (iText 7) =============

    /**
     * Escribe el header estandar en un {@link Document} de iText 7.
     * Llamar inmediatamente despues de crear el Document, antes del contenido
     * principal del reporte.
     */
    public static void writePdfHeader(Document doc, ReportContext ctx) {
        try {
            PdfFont fontBold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontRegular = PdfFontFactory.createFont("Helvetica");

            // Titulo del reporte
            doc.add(new Paragraph(ctx.reportTitle)
                    .setFont(fontBold).setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            // Tabla de metadata 2 columnas
            Table meta = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                    .useAllAvailableWidth();
            meta.setBorder(new SolidBorder(new DeviceRgb(180, 180, 180), 0.5f));

            addMetaRow(meta, fontBold, fontRegular, "Empresa",
                    ctx.companyName + (ctx.companyNit != null ? " (NIT " + ctx.companyNit + ")" : ""));
            addMetaRow(meta, fontBold, fontRegular, "Generado por",
                    ctx.userEmail + (ctx.roles.isEmpty() ? "" : " [" + String.join(", ", ctx.roles) + "]"));
            addMetaRow(meta, fontBold, fontRegular, "Fecha generacion",
                    ctx.generatedAt.format(DT_FMT));

            if (ctx.filters != null && !ctx.filters.isEmpty()) {
                StringBuilder fSb = new StringBuilder();
                ctx.filters.forEach((k, v) -> {
                    if (fSb.length() > 0) fSb.append(" | ");
                    fSb.append(k).append(": ").append(v);
                });
                addMetaRow(meta, fontBold, fontRegular, "Filtros aplicados", fSb.toString());
            }

            doc.add(meta);

            // Totales (tabla aparte mas prominente)
            if (ctx.totals != null && !ctx.totals.isEmpty()) {
                doc.add(new Paragraph("\n").setFontSize(4));
                Table tot = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                        .useAllAvailableWidth();
                tot.setBorder(new SolidBorder(new DeviceRgb(0, 86, 179), 1f));
                Cell totH = new Cell(1, 2)
                        .add(new Paragraph("Totales")
                                .setFont(fontBold).setFontSize(10).setFontColor(new DeviceRgb(255, 255, 255)))
                        .setBackgroundColor(new DeviceRgb(0, 86, 179));
                tot.addCell(totH);
                ctx.totals.forEach((k, v) -> {
                    tot.addCell(new Cell().add(new Paragraph(k).setFont(fontBold).setFontSize(9)));
                    tot.addCell(new Cell().add(new Paragraph(formatCurrency(v))
                            .setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
                });
                doc.add(tot);
            }

            doc.add(new Paragraph("\n").setFontSize(6));
        } catch (Exception ex) {
            // Defensive: si el header falla, NO romper el reporte completo
            doc.add(new Paragraph("(no se pudo renderizar header: " + ex.getMessage() + ")")
                    .setFontSize(8));
        }
    }

    private static void addMetaRow(Table t, PdfFont bold, PdfFont reg, String key, String val) {
        t.addCell(new Cell().add(new Paragraph(key).setFont(bold).setFontSize(9)));
        t.addCell(new Cell().add(new Paragraph(val == null ? "-" : val).setFont(reg).setFontSize(9)));
    }

    // ============= XLSX (Apache POI) =============

    /**
     * Escribe el header estandar en un Sheet de Apache POI a partir del row 0.
     * Retorna el numero de filas usadas para que el caller continue debajo.
     * Si {@code totalCols > 0}, mergea las filas del header sobre {@code totalCols}
     * columnas para que ocupe todo el ancho de la tabla.
     */
    public static int writeXlsxHeader(Workbook wb, Sheet sheet, ReportContext ctx, int totalCols) {
        try {
            Font fb = wb.createFont(); fb.setBold(true); fb.setFontHeightInPoints((short) 11);
            CellStyle title = wb.createCellStyle(); title.setFont(fb);
            title.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font reg = wb.createFont(); reg.setFontHeightInPoints((short) 9);
            CellStyle key = wb.createCellStyle();
            Font keyF = wb.createFont(); keyF.setBold(true); keyF.setFontHeightInPoints((short) 9);
            key.setFont(keyF);
            CellStyle val = wb.createCellStyle(); val.setFont(reg);

            int r = 0;
            Row rowTitle = sheet.createRow(r++);
            rowTitle.createCell(0).setCellValue(ctx.reportTitle);
            rowTitle.getCell(0).setCellStyle(title);
            if (totalCols > 1) sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(0, totalCols - 1)));

            r = appendXlsxRow(sheet, r, "Empresa",
                    ctx.companyName + (ctx.companyNit != null ? " (NIT " + ctx.companyNit + ")" : ""),
                    key, val, totalCols);
            r = appendXlsxRow(sheet, r, "Generado por",
                    ctx.userEmail + (ctx.roles.isEmpty() ? "" : " [" + String.join(", ", ctx.roles) + "]"),
                    key, val, totalCols);
            r = appendXlsxRow(sheet, r, "Fecha generacion",
                    ctx.generatedAt.format(DT_FMT), key, val, totalCols);

            if (ctx.filters != null && !ctx.filters.isEmpty()) {
                StringBuilder fSb = new StringBuilder();
                ctx.filters.forEach((k, v) -> {
                    if (fSb.length() > 0) fSb.append(" | ");
                    fSb.append(k).append(": ").append(v);
                });
                r = appendXlsxRow(sheet, r, "Filtros aplicados", fSb.toString(), key, val, totalCols);
            }

            // Totales como filas adicionales (estilo destacado)
            if (ctx.totals != null && !ctx.totals.isEmpty()) {
                Font totF = wb.createFont(); totF.setBold(true); totF.setFontHeightInPoints((short) 10);
                CellStyle totK = wb.createCellStyle(); totK.setFont(totF);
                totK.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
                totK.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                CellStyle totV = wb.createCellStyle(); totV.setFont(totF);
                totV.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
                totV.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                totV.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

                for (Map.Entry<String, BigDecimal> e : ctx.totals.entrySet()) {
                    Row tr = sheet.createRow(r++);
                    tr.createCell(0).setCellValue(e.getKey());
                    tr.getCell(0).setCellStyle(totK);
                    tr.createCell(1).setCellValue(e.getValue().doubleValue());
                    tr.getCell(1).setCellStyle(totV);
                    if (totalCols > 2) {
                        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 1, Math.max(1, totalCols - 1)));
                    }
                }
            }

            // Linea en blanco antes del contenido
            r++;
            return r;
        } catch (Exception ex) {
            // Defensive
            return 0;
        }
    }

    private static int appendXlsxRow(Sheet sheet, int r, String k, String v, CellStyle keyStyle, CellStyle valStyle, int totalCols) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(k);
        row.getCell(0).setCellStyle(keyStyle);
        row.createCell(1).setCellValue(v == null ? "-" : v);
        row.getCell(1).setCellStyle(valStyle);
        if (totalCols > 2) {
            sheet.addMergedRegion(new CellRangeAddress(r, r, 1, Math.max(1, totalCols - 1)));
        }
        return r + 1;
    }

    // ============= CSV =============

    /**
     * Construye lineas de comentario tipo "# clave: valor" para prepend a un CSV.
     * Excel y Google Sheets tratan estas lineas como filas regulares; por eso el
     * separador es "; " (UTF-8 BOM ya lo agrega el writer del caller).
     */
    public static String buildCsvHeader(ReportContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(ctx.reportTitle).append('\n');
        sb.append("# Empresa: ").append(ctx.companyName);
        if (ctx.companyNit != null) sb.append(" (NIT ").append(ctx.companyNit).append(")");
        sb.append('\n');
        sb.append("# Generado por: ").append(ctx.userEmail);
        if (!ctx.roles.isEmpty()) sb.append(" [").append(String.join(", ", ctx.roles)).append("]");
        sb.append('\n');
        sb.append("# Fecha generacion: ").append(ctx.generatedAt.format(DT_FMT)).append('\n');

        if (ctx.filters != null && !ctx.filters.isEmpty()) {
            sb.append("# Filtros: ");
            int i = 0;
            for (Map.Entry<String, String> e : ctx.filters.entrySet()) {
                if (i++ > 0) sb.append(" | ");
                sb.append(e.getKey()).append("=").append(e.getValue());
            }
            sb.append('\n');
        }

        if (ctx.totals != null && !ctx.totals.isEmpty()) {
            sb.append("# Totales:\n");
            ctx.totals.forEach((k, v) -> sb.append("#   ").append(k).append(": ").append(formatCurrency(v)).append('\n'));
        }
        sb.append("#\n"); // separador antes de la fila de cabeceras de datos
        return sb.toString();
    }

    private static String formatCurrency(BigDecimal v) {
        if (v == null) return "-";
        return String.format("%,.2f", v);
    }
}

package com.sigcon.backend.utils.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

/**
 * Helper compartido para exportacion tabular sencilla (CSV con BOM UTF-8 y XLSX).
 *
 * <p>Usado por los modulos CFG (HU-CFG-RF-09 E8 / 13 E7 / 17 E5 / 21 E5 / 25 E3)
 * y replicable por cualquier otro listado simple. NO maneja totales ni formatos
 * complejos; para reportes contables formales usar {@code AccountingBookPdfService}
 * o {@code JournalEntryExportService}.
 *
 * <p>Uso tipico:
 * <pre>
 *   List&lt;String&gt; headers = List.of("Id","Codigo","Nombre");
 *   List&lt;Function&lt;MyDto, Object&gt;&gt; cols = List.of(
 *       MyDto::getId, MyDto::getCode, MyDto::getName);
 *   byte[] csv  = SimpleTableExporter.toCsv(headers, cols, rows);
 *   byte[] xlsx = SimpleTableExporter.toXlsx("Sheet", headers, cols, rows);
 * </pre>
 */
public final class SimpleTableExporter {

    private SimpleTableExporter() {}

    /** MIME para CSV con UTF-8. */
    public static final String CSV_MIME = "text/csv; charset=utf-8";

    /** MIME estandar para XLSX. */
    public static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** MIME para PDF. */
    public static final String PDF_MIME = "application/pdf";

    /**
     * Genera CSV con BOM UTF-8 (Excel ES detecta acentos correctamente).
     * Separador ';' (punto y coma) por convencion ES.
     */
    public static <T> byte[] toCsv(List<String> headers,
                                   List<Function<T, Object>> columns,
                                   List<T> rows) {
        return toCsv(headers, columns, rows, null);
    }

    /**
     * QA Bloque BJ (2026-05-17): variante con header estandar opcional.
     * Si {@code reportCtx != null}, se prepende un bloque de comentarios CSV
     * con empresa, usuario, rol, fecha, filtros y totales antes del header de
     * datos.
     */
    public static <T> byte[] toCsv(List<String> headers,
                                   List<Function<T, Object>> columns,
                                   List<T> rows,
                                   ReportHeaderBuilder.ReportContext reportCtx) {
        return toCsv(headers, columns, rows, reportCtx, null);
    }

    /**
     * QA Bloque BN (2026-05-18): variante con fila TOTAL opcional al final.
     * Si {@code totalsRow != null}, se agrega una fila adicional al cierre con
     * los valores precalculados. La cantidad de columnas de la fila debe ser
     * igual a {@code headers.size()}. Util para reportes contables donde se
     * exige sumatoria de Subtotal, IVA, Retencion, Total, Saldo, etc.
     */
    public static <T> byte[] toCsv(List<String> headers,
                                   List<Function<T, Object>> columns,
                                   List<T> rows,
                                   ReportHeaderBuilder.ReportContext reportCtx,
                                   List<Object> totalsRow) {
        StringBuilder sb = new StringBuilder();
        // BOM UTF-8 para Excel ES
        sb.append('﻿');
        // Header estandar (empresa, usuario, etc.)
        if (reportCtx != null) sb.append(ReportHeaderBuilder.buildCsvHeader(reportCtx));
        // Header de datos
        sb.append(String.join(";", headers)).append('\n');
        // Body
        for (T row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                Object v = columns.get(i).apply(row);
                sb.append(escapeCsv(v));
                if (i < columns.size() - 1) sb.append(';');
            }
            sb.append('\n');
        }
        // Fila TOTAL (Bloque BN). El caller pasa la lista ya calculada con
        // strings para columnas no agregables ("TOTAL", "") y numeros/strings
        // formateados para las columnas numericas.
        if (totalsRow != null && !totalsRow.isEmpty()) {
            for (int i = 0; i < totalsRow.size(); i++) {
                sb.append(escapeCsv(totalsRow.get(i)));
                if (i < totalsRow.size() - 1) sb.append(';');
            }
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Genera XLSX (Apache POI) con header en bold + freeze panes en fila 1.
     */
    public static <T> byte[] toXlsx(String sheetName,
                                    List<String> headers,
                                    List<Function<T, Object>> columns,
                                    List<T> rows) {
        return toXlsx(sheetName, headers, columns, rows, null);
    }

    /**
     * QA Bloque BJ (2026-05-17): variante con header estandar opcional.
     * Si {@code reportCtx != null}, escribe el bloque de empresa+usuario+rol+
     * filtros+totales antes del header de datos.
     */
    public static <T> byte[] toXlsx(String sheetName,
                                    List<String> headers,
                                    List<Function<T, Object>> columns,
                                    List<T> rows,
                                    ReportHeaderBuilder.ReportContext reportCtx) {
        return toXlsx(sheetName, headers, columns, rows, reportCtx, null);
    }

    /**
     * QA Bloque BN (2026-05-18): variante con fila TOTAL opcional al final del
     * XLSX. Se renderiza con bold + fondo azul claro destacado para que el
     * contador la identifique de un vistazo.
     */
    public static <T> byte[] toXlsx(String sheetName,
                                    List<String> headers,
                                    List<Function<T, Object>> columns,
                                    List<T> rows,
                                    ReportHeaderBuilder.ReportContext reportCtx,
                                    List<Object> totalsRow) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName != null ? sheetName : "Datos");

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            int startRow = 0;
            if (reportCtx != null) {
                startRow = ReportHeaderBuilder.writeXlsxHeader(wb, sheet, reportCtx, headers.size());
            }

            Row headerRow = sheet.createRow(startRow);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, startRow + 1);

            // Body
            int r = startRow + 1;
            for (T row : rows) {
                Row xr = sheet.createRow(r++);
                for (int i = 0; i < columns.size(); i++) {
                    Object v = columns.get(i).apply(row);
                    Cell c = xr.createCell(i);
                    if (v == null) {
                        c.setBlank();
                    } else if (v instanceof Number) {
                        c.setCellValue(((Number) v).doubleValue());
                    } else if (v instanceof Boolean) {
                        c.setCellValue((Boolean) v);
                    } else {
                        c.setCellValue(v.toString());
                    }
                }
            }

            // Fila TOTAL (Bloque BN): bold + fondo azul claro
            if (totalsRow != null && !totalsRow.isEmpty()) {
                CellStyle totalStyle = wb.createCellStyle();
                totalStyle.setFont(bold);
                totalStyle.setFillForegroundColor(
                        org.apache.poi.ss.usermodel.IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
                totalStyle.setFillPattern(
                        org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                Row totalRow = sheet.createRow(r);
                for (int i = 0; i < totalsRow.size(); i++) {
                    Object v = totalsRow.get(i);
                    Cell c = totalRow.createCell(i);
                    c.setCellStyle(totalStyle);
                    if (v == null) {
                        c.setBlank();
                    } else if (v instanceof Number) {
                        c.setCellValue(((Number) v).doubleValue());
                    } else {
                        c.setCellValue(v.toString());
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error generando XLSX: " + e.getMessage(), e);
        }
    }

    /**
     * QA Bloque BR (HU-CG-12 E2): genera PDF tabular generico con iText 7.
     * Mismo contrato (headers + columns + rows + ReportContext + totalsRow) que
     * toCsv/toXlsx, para que cualquier modulo (tributarios, listados CFG, etc.)
     * tenga exportacion consistente en los 3 formatos. Encabezado A4 con titulo,
     * datos de empresa/usuario/filtros del ReportContext, tabla con header
     * resaltado, fila TOTAL opcional y bloque resumen de totales del contexto.
     *
     * @param title     titulo del reporte (ej. "Resumen Impuestos 2026")
     * @param headers   encabezados de columna
     * @param columns   extractores de valor por columna
     * @param rows      filas de datos
     * @param reportCtx contexto (empresa/usuario/filtros/totales); puede ser null
     * @param totalsRow fila TOTAL precalculada; puede ser null
     */
    public static <T> byte[] toPdf(String title,
                                   List<String> headers,
                                   List<Function<T, Object>> columns,
                                   List<T> rows,
                                   ReportHeaderBuilder.ReportContext reportCtx,
                                   List<Object> totalsRow) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);
            try (Document doc = new Document(pdf)) {
                PdfFont titleFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                PdfFont bodyFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                doc.add(new Paragraph(title != null ? title : "Reporte")
                        .setFont(titleFont).setFontSize(16)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(new DeviceRgb(30, 58, 138)));

                if (reportCtx != null) {
                    StringBuilder meta = new StringBuilder();
                    if (reportCtx.companyName != null) {
                        meta.append("Empresa: ").append(reportCtx.companyName);
                        if (reportCtx.companyNit != null) meta.append(" - NIT ").append(reportCtx.companyNit);
                        meta.append('\n');
                    }
                    if (reportCtx.userEmail != null) {
                        meta.append("Generado por: ").append(reportCtx.userEmail);
                        if (reportCtx.roles != null && !reportCtx.roles.isEmpty()) {
                            meta.append(" (").append(String.join(", ", reportCtx.roles)).append(")");
                        }
                        meta.append('\n');
                    }
                    meta.append("Generado: ").append(LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    if (reportCtx.filters != null && !reportCtx.filters.isEmpty()) {
                        meta.append("\nFiltros: ");
                        reportCtx.filters.forEach((k, v) -> meta.append(k).append("=").append(v).append("; "));
                    }
                    doc.add(new Paragraph(meta.toString()).setFont(bodyFont).setFontSize(9)
                            .setFontColor(new DeviceRgb(75, 85, 99)));
                }

                DecimalFormat df = new DecimalFormat("#,##0.00",
                        new DecimalFormatSymbols(new Locale("es", "CO")));
                Table table = new Table(UnitValue.createPercentArray(headers.size()))
                        .useAllAvailableWidth();
                for (String h : headers) {
                    table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                            .add(new Paragraph(h).setFont(titleFont).setFontSize(9))
                            .setBackgroundColor(new DeviceRgb(238, 242, 255)));
                }
                for (T row : rows) {
                    for (Function<T, Object> col : columns) {
                        Object v = col.apply(row);
                        String text = v == null ? "" : (v instanceof Number n ? df.format(n.doubleValue()) : v.toString());
                        table.addCell(new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph(text).setFont(bodyFont).setFontSize(8)));
                    }
                }
                if (totalsRow != null && !totalsRow.isEmpty()) {
                    for (Object v : totalsRow) {
                        String text = v == null ? "" : (v instanceof Number n ? df.format(n.doubleValue()) : v.toString());
                        table.addCell(new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph(text).setFont(titleFont).setFontSize(9))
                                .setBackgroundColor(new DeviceRgb(217, 226, 243)));
                    }
                }
                doc.add(table);

                if (reportCtx != null && reportCtx.totals != null && !reportCtx.totals.isEmpty()) {
                    StringBuilder sum = new StringBuilder("\nResumen:\n");
                    reportCtx.totals.forEach((k, v) -> sum.append("  ").append(k).append(": ")
                            .append(v != null ? df.format(v.doubleValue()) : "0.00").append('\n'));
                    doc.add(new Paragraph(sum.toString()).setFont(bodyFont).setFontSize(9));
                }

                doc.add(new Paragraph("Documento generado automaticamente. Pagina con valor probatorio.")
                        .setFont(bodyFont).setFontSize(7)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.GRAY));
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Escapa un valor para CSV: si contiene ;, ", \n, \r → encierra en comillas
     * y duplica las comillas internas.
     */
    private static String escapeCsv(Object v) {
        if (v == null) return "";
        String s = v.toString();
        boolean needsQuote = s.contains(";") || s.contains("\"")
                || s.contains("\n") || s.contains("\r");
        if (!needsQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}

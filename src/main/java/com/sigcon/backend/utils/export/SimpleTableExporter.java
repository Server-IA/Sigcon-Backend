package com.sigcon.backend.utils.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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

    /**
     * Genera CSV con BOM UTF-8 (Excel ES detecta acentos correctamente).
     * Separador ';' (punto y coma) por convencion ES.
     */
    public static <T> byte[] toCsv(List<String> headers,
                                   List<Function<T, Object>> columns,
                                   List<T> rows) {
        StringBuilder sb = new StringBuilder();
        // BOM UTF-8 para Excel ES
        sb.append('﻿');
        // Header
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
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Genera XLSX (Apache POI) con header en bold + freeze panes en fila 1.
     */
    public static <T> byte[] toXlsx(String sheetName,
                                    List<String> headers,
                                    List<Function<T, Object>> columns,
                                    List<T> rows) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName != null ? sheetName : "Datos");

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, 1);

            // Body
            int r = 1;
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

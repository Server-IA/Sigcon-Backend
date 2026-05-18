package com.sigcon.backend.general.accounting.journal.domain.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntryLine;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.parametrization.parameters.domain.service.SystemInfoService;
import com.sigcon.backend.utils.export.ReportContextResolver;
import com.sigcon.backend.utils.export.ReportHeaderBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Servicio de exportacion de un comprobante contable individual a PDF y Excel.
 *
 * <p>Cubre HU-CG-01C (Visualizacion y Exportacion de Comprobantes). Genera dos
 * formatos:</p>
 * <ul>
 *   <li>PDF: layout limpio con cabecera de empresa + datos del comprobante +
 *       tabla de lineas con totales y badge de estado.</li>
 *   <li>Excel (.xlsx): hoja unica con el mismo contenido en celdas; util para
 *       conciliaciones y archivo del contador.</li>
 * </ul>
 *
 * <p>No depende de Jasper ni de Freemarker; iText7 (ya en pom) y Apache POI
 * cubren ambos formatos sin nuevas dependencias.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JournalEntryExportService {

    private final JournalEntryRepository journalEntryRepository;
    private final SystemInfoService systemInfoService;
    // QA Bloque BJ (2026-05-17): header estandar (empresa, usuario, rol, fecha,
    // filtros, totales) en TODA exportacion. Reemplaza el header decorativo
    // antiguo manteniendo el detalle de lineas del comprobante.
    private final ReportContextResolver reportContextResolver;

    private static final DeviceRgb BRAND_PRIMARY  = new DeviceRgb(30, 58, 138);
    private static final DeviceRgb HEADER_BG      = new DeviceRgb(238, 242, 255);
    private static final DeviceRgb SUBTLE         = new DeviceRgb(107, 114, 128);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Genera el PDF del comprobante con cabecera, lineas y totales.
     * @param entryId identificador del asiento
     * @return bytes del PDF
     */
    public byte[] generatePdf(Long entryId) {
        JournalEntry entry = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado: " + entryId));

        DecimalFormat money = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("es", "CO")));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.LETTER)) {

            doc.setMargins(36, 36, 36, 36);

            // QA Bloque BJ (2026-05-17): header estandar (empresa+usuario+rol+fecha+filtros+totales).
            // Sustituye el bloque manual de cabecera empresa+titulo por uno
            // consistente con el resto de exportaciones.
            String voucherCode = JournalEntryService.buildVoucherCode(entry);
            BigDecimal sumD0 = BigDecimal.ZERO, sumC0 = BigDecimal.ZERO;
            if (entry.getLines() != null) {
                for (JournalEntryLine l : entry.getLines()) {
                    if (l.getDebitAmount() != null) sumD0 = sumD0.add(l.getDebitAmount());
                    if (l.getCreditAmount() != null) sumC0 = sumC0.add(l.getCreditAmount());
                }
            }
            ReportHeaderBuilder.ReportContext headerCtx = reportContextResolver
                    .baseContext("Comprobante contable " + voucherCode)
                    .addFilter("Fecha del asiento", entry.getEntryDate() != null ? entry.getEntryDate().format(DATE_FMT) : "-")
                    .addFilter("Estado", entry.getStatus() != null ? entry.getStatus().name() : "-")
                    .addFilter("Modulo origen", entry.getSourceModule() != null ? entry.getSourceModule().name() : "-")
                    .addTotal("Total Debito", sumD0)
                    .addTotal("Total Credito", sumC0)
                    .build();
            ReportHeaderBuilder.writePdfHeader(doc, headerCtx);

            // Datos cabecera
            Table head = new Table(UnitValue.createPercentArray(new float[]{1, 2, 1, 2}))
                    .useAllAvailableWidth().setMarginTop(8);
            head.addCell(headCell("Fecha"));
            head.addCell(valueCell(entry.getEntryDate() != null ? entry.getEntryDate().format(DATE_FMT) : "-"));
            head.addCell(headCell("Estado"));
            head.addCell(valueCell(entry.getStatus() != null ? entry.getStatus().name() : "-"));

            head.addCell(headCell("Modulo origen"));
            head.addCell(valueCell(entry.getSourceModule() != null ? entry.getSourceModule().name() : "-"));
            head.addCell(headCell("Anio fiscal"));
            head.addCell(valueCell(entry.getFiscalYear() != null ? entry.getFiscalYear().toString() : "-"));

            head.addCell(headCell("Descripcion"));
            Cell descCell = valueCell(safe(entry.getDescription(), "-"));
            // colSpan a 3
            head.addCell(descCell);
            head.addCell(new Cell(1, 2).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            doc.add(head);

            if (entry.getReversalOf() != null) {
                doc.add(new Paragraph("Reversa al comprobante " + JournalEntryService.buildVoucherCode(entry.getReversalOf()))
                        .setItalic().setFontSize(9).setFontColor(SUBTLE).setMarginTop(4));
            }

            // Lineas
            doc.add(new Paragraph("Detalle de lineas").setBold().setFontSize(11).setMarginTop(12));
            Table lines = new Table(UnitValue.createPercentArray(new float[]{2, 4, 2, 2, 2, 2}))
                    .useAllAvailableWidth();
            lines.addHeaderCell(headCell("Cuenta"));
            lines.addHeaderCell(headCell("Descripcion"));
            lines.addHeaderCell(headCell("Tercero"));
            lines.addHeaderCell(headCell("Centro Costo"));
            lines.addHeaderCell(headCell("Debito"));
            lines.addHeaderCell(headCell("Credito"));

            BigDecimal sumD = BigDecimal.ZERO, sumC = BigDecimal.ZERO;
            if (entry.getLines() != null) {
                for (JournalEntryLine l : entry.getLines()) {
                    String pucCode = l.getAccountingAccount() != null && l.getAccountingAccount().getPucAccount() != null
                            ? l.getAccountingAccount().getPucAccount().getCode() : "-";
                    String pucName = l.getAccountingAccount() != null && l.getAccountingAccount().getPucAccount() != null
                            ? l.getAccountingAccount().getPucAccount().getName() : "";
                    lines.addCell(valueCell(pucCode + (pucName.isEmpty() ? "" : " " + pucName)));
                    lines.addCell(valueCell(safe(l.getDescription(), "-")));
                    lines.addCell(valueCell(safe(l.getThirdPartyNit(), "-")));
                    lines.addCell(valueCell(l.getCostCenter() != null ? l.getCostCenter().getName() : "-"));
                    lines.addCell(valueCell(money.format(l.getDebitAmount() != null ? l.getDebitAmount() : BigDecimal.ZERO))
                            .setTextAlignment(TextAlignment.RIGHT));
                    lines.addCell(valueCell(money.format(l.getCreditAmount() != null ? l.getCreditAmount() : BigDecimal.ZERO))
                            .setTextAlignment(TextAlignment.RIGHT));
                    if (l.getDebitAmount() != null) sumD = sumD.add(l.getDebitAmount());
                    if (l.getCreditAmount() != null) sumC = sumC.add(l.getCreditAmount());
                }
            }
            // Totales
            lines.addCell(new Cell(1, 4).add(new Paragraph("TOTALES").setBold())
                    .setBackgroundColor(HEADER_BG).setTextAlignment(TextAlignment.RIGHT));
            lines.addCell(new Cell().add(new Paragraph(money.format(sumD)).setBold())
                    .setBackgroundColor(HEADER_BG).setTextAlignment(TextAlignment.RIGHT));
            lines.addCell(new Cell().add(new Paragraph(money.format(sumC)).setBold())
                    .setBackgroundColor(HEADER_BG).setTextAlignment(TextAlignment.RIGHT));
            doc.add(lines);

            doc.add(new Paragraph("Documento generado por SIGCON. Sistema de Gestion Contable.")
                    .setFontSize(8).setFontColor(SUBTLE).setMarginTop(10).setTextAlignment(TextAlignment.CENTER));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF de comprobante {}: {}", entryId, e.getMessage(), e);
            throw new RuntimeException("Error generando PDF del comprobante: " + e.getMessage(), e);
        }
    }

    /**
     * Genera el Excel del comprobante: una hoja con la misma cabecera y detalle.
     * @param entryId identificador del asiento
     * @return bytes del .xlsx
     */
    public byte[] generateXlsx(Long entryId) {
        JournalEntry entry = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado: " + entryId));

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            String voucherCode = JournalEntryService.buildVoucherCode(entry);
            Sheet sh = wb.createSheet(voucherCode);

            // HU-CG-02C E3: estilos consistentes y formato moneda en lugar de Double crudo.
            // Antes el Excel salia con merge desalineado (6 cols vs tabla 7) + numeros sin
            // formato + sin bordes en la tabla. Esto rompia el flujo de QA porque el contador
            // no podia auditar el comprobante exportado contra el original.
            DataFormat fmt = wb.createDataFormat();

            CellStyle boldStyle = wb.createCellStyle();
            Font boldFont = wb.createFont(); boldFont.setBold(true); boldStyle.setFont(boldFont);

            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 13);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle headerCellStyle = wb.createCellStyle();
            Font headerFont = wb.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerCellStyle.setFont(headerFont);
            headerCellStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
            applyAllBorders(headerCellStyle);

            CellStyle bodyStyle = wb.createCellStyle();
            applyAllBorders(bodyStyle);

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.cloneStyleFrom(bodyStyle);
            moneyStyle.setDataFormat(fmt.getFormat("#,##0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle moneyTotalStyle = wb.createCellStyle();
            moneyTotalStyle.cloneStyleFrom(moneyStyle);
            moneyTotalStyle.setFont(boldFont);
            moneyTotalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            moneyTotalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle totalsLabelStyle = wb.createCellStyle();
            totalsLabelStyle.cloneStyleFrom(bodyStyle);
            totalsLabelStyle.setFont(boldFont);
            totalsLabelStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalsLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalsLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] cols = {"Cuenta PUC", "Nombre cuenta", "Descripcion", "Tercero NIT", "Centro Costo", "Debito", "Credito"};
            int totalCols = cols.length; // 7

            // QA Bloque BJ (2026-05-17): header estandar (empresa+usuario+rol+fecha+
            // filtros+totales) reemplaza el bloque manual previo. Antes salia
            // empresa+nit+titulo+5 filas de cabecera. Ahora helper transversal.
            BigDecimal sumDp = BigDecimal.ZERO, sumCp = BigDecimal.ZERO;
            if (entry.getLines() != null) {
                for (JournalEntryLine l : entry.getLines()) {
                    if (l.getDebitAmount() != null) sumDp = sumDp.add(l.getDebitAmount());
                    if (l.getCreditAmount() != null) sumCp = sumCp.add(l.getCreditAmount());
                }
            }
            ReportHeaderBuilder.ReportContext xlsxCtx = reportContextResolver
                    .baseContext("Comprobante contable " + voucherCode)
                    .addFilter("Fecha del asiento", entry.getEntryDate() != null ? entry.getEntryDate().format(DATE_FMT) : "-")
                    .addFilter("Estado", entry.getStatus() != null ? entry.getStatus().name() : "-")
                    .addFilter("Modulo origen", entry.getSourceModule() != null ? entry.getSourceModule().name() : "-")
                    .addFilter("Anio fiscal", entry.getFiscalYear() != null ? entry.getFiscalYear().toString() : "-")
                    .addFilter("Descripcion del asiento", safe(entry.getDescription(), "-"))
                    .addTotal("Total Debito", sumDp)
                    .addTotal("Total Credito", sumCp)
                    .build();
            int r = ReportHeaderBuilder.writeXlsxHeader(wb, sh, xlsxCtx, totalCols);

            // Tabla de lineas con header destacado
            Row hdr = sh.createRow(r++);
            int headerRowIndex = hdr.getRowNum();
            for (int i = 0; i < cols.length; i++) {
                org.apache.poi.ss.usermodel.Cell c = hdr.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerCellStyle);
            }

            BigDecimal sumD = BigDecimal.ZERO, sumC = BigDecimal.ZERO;
            if (entry.getLines() != null) {
                for (JournalEntryLine l : entry.getLines()) {
                    Row row = sh.createRow(r++);
                    String pucCode = l.getAccountingAccount() != null && l.getAccountingAccount().getPucAccount() != null
                            ? l.getAccountingAccount().getPucAccount().getCode() : "-";
                    String pucName = l.getAccountingAccount() != null && l.getAccountingAccount().getPucAccount() != null
                            ? cleanName(l.getAccountingAccount().getPucAccount().getName(), pucCode) : "";
                    setBodyCell(row.createCell(0), pucCode, bodyStyle);
                    setBodyCell(row.createCell(1), pucName, bodyStyle);
                    setBodyCell(row.createCell(2), safe(l.getDescription(), ""), bodyStyle);
                    setBodyCell(row.createCell(3), safe(l.getThirdPartyNit(), ""), bodyStyle);
                    setBodyCell(row.createCell(4), l.getCostCenter() != null ? l.getCostCenter().getName() : "", bodyStyle);
                    org.apache.poi.ss.usermodel.Cell debitC = row.createCell(5);
                    debitC.setCellValue(l.getDebitAmount() != null ? l.getDebitAmount().doubleValue() : 0d);
                    debitC.setCellStyle(moneyStyle);
                    org.apache.poi.ss.usermodel.Cell creditC = row.createCell(6);
                    creditC.setCellValue(l.getCreditAmount() != null ? l.getCreditAmount().doubleValue() : 0d);
                    creditC.setCellStyle(moneyStyle);
                    if (l.getDebitAmount() != null) sumD = sumD.add(l.getDebitAmount());
                    if (l.getCreditAmount() != null) sumC = sumC.add(l.getCreditAmount());
                }
            }
            // Fila de totales con merge "TOTALES" en cols 0..4 + valores en 5 y 6
            Row totals = sh.createRow(r++);
            int totalsRow = totals.getRowNum();
            org.apache.poi.ss.usermodel.Cell labelCell = totals.createCell(0);
            labelCell.setCellValue("TOTALES");
            labelCell.setCellStyle(totalsLabelStyle);
            for (int i = 1; i <= 4; i++) {
                org.apache.poi.ss.usermodel.Cell empty = totals.createCell(i);
                empty.setCellStyle(totalsLabelStyle);
            }
            sh.addMergedRegion(new CellRangeAddress(totalsRow, totalsRow, 0, 4));
            org.apache.poi.ss.usermodel.Cell sumDCell = totals.createCell(5);
            sumDCell.setCellValue(sumD.doubleValue());
            sumDCell.setCellStyle(moneyTotalStyle);
            org.apache.poi.ss.usermodel.Cell sumCCell = totals.createCell(6);
            sumCCell.setCellValue(sumC.doubleValue());
            sumCCell.setCellStyle(moneyTotalStyle);

            // Auto-size + freeze panes
            for (int i = 0; i < cols.length; i++) sh.autoSizeColumn(i);
            sh.createFreezePane(0, headerRowIndex + 1);

            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generando XLSX de comprobante {}: {}", entryId, e.getMessage(), e);
            throw new RuntimeException("Error generando Excel del comprobante: " + e.getMessage(), e);
        }
    }

    private static String safe(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }

    /**
     * Helper Apache POI: aplica los 4 bordes a un CellStyle.
     */
    private static void applyAllBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    /**
     * Asigna texto + estilo a una celda en una sola llamada (reduce ruido).
     */
    private static void setBodyCell(org.apache.poi.ss.usermodel.Cell cell, String value, CellStyle style) {
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /**
     * HU-CG-02C E3: la columna "Cuenta PUC" ya muestra el codigo. Si el name de
     * la cuenta termina con " (codigo)" -ej. "Bancos (1110)"- lo recortamos para
     * no duplicar "1110" en col A y "Bancos (1110)" en col B. Mejora la legibilidad
     * del Excel sin perder informacion.
     */
    private static String cleanName(String name, String pucCode) {
        if (name == null || pucCode == null) return name == null ? "" : name;
        String suffix = " (" + pucCode + ")";
        if (name.endsWith(suffix)) {
            return name.substring(0, name.length() - suffix.length()).trim();
        }
        return name;
    }

    private static Cell headCell(String text) {
        return new Cell().add(new Paragraph(text).setBold().setFontSize(9))
                .setBackgroundColor(HEADER_BG);
    }

    private static Cell valueCell(String text) {
        return new Cell().add(new Paragraph(text).setFontSize(9));
    }
}

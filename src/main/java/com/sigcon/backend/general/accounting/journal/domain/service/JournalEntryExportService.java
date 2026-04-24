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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
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

            // Cabecera empresa
            String companyName = safe(systemInfoService.getCompanyName(), "SIGCON");
            String companyNit  = safe(systemInfoService.getCompanyNit(), "");
            doc.add(new Paragraph(companyName)
                    .setBold().setFontSize(14).setFontColor(BRAND_PRIMARY));
            if (!companyNit.isEmpty()) {
                doc.add(new Paragraph("NIT: " + companyNit).setFontSize(9).setFontColor(SUBTLE));
            }

            // Titulo
            String voucherCode = JournalEntryService.buildVoucherCode(entry);
            doc.add(new Paragraph("COMPROBANTE CONTABLE " + voucherCode)
                    .setBold().setFontSize(13).setMarginTop(12));

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

            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont(); f.setBold(true); bold.setFont(f);

            int r = 0;
            // Cabecera empresa
            Row r0 = sh.createRow(r++);
            r0.createCell(0).setCellValue("Empresa");
            r0.createCell(1).setCellValue(safe(systemInfoService.getCompanyName(), "SIGCON"));
            r0.getCell(0).setCellStyle(bold);

            Row r1 = sh.createRow(r++);
            r1.createCell(0).setCellValue("NIT");
            r1.createCell(1).setCellValue(safe(systemInfoService.getCompanyNit(), ""));
            r1.getCell(0).setCellStyle(bold);

            r++; // espacio

            // Titulo
            Row title = sh.createRow(r++);
            title.createCell(0).setCellValue("COMPROBANTE CONTABLE " + voucherCode);
            title.getCell(0).setCellStyle(bold);
            sh.addMergedRegion(new CellRangeAddress(title.getRowNum(), title.getRowNum(), 0, 5));

            // Cabecera datos
            String[][] headerData = {
                    {"Fecha", entry.getEntryDate() != null ? entry.getEntryDate().toString() : "-"},
                    {"Estado", entry.getStatus() != null ? entry.getStatus().name() : "-"},
                    {"Modulo origen", entry.getSourceModule() != null ? entry.getSourceModule().name() : "-"},
                    {"Anio fiscal", entry.getFiscalYear() != null ? entry.getFiscalYear().toString() : "-"},
                    {"Descripcion", safe(entry.getDescription(), "-")},
            };
            for (String[] kv : headerData) {
                Row hr = sh.createRow(r++);
                hr.createCell(0).setCellValue(kv[0]);
                hr.createCell(1).setCellValue(kv[1]);
                hr.getCell(0).setCellStyle(bold);
            }
            r++; // espacio

            // Tabla lineas
            String[] cols = {"Cuenta PUC", "Nombre cuenta", "Descripcion", "Tercero NIT", "Centro Costo", "Debito", "Credito"};
            Row hdr = sh.createRow(r++);
            for (int i = 0; i < cols.length; i++) {
                org.apache.poi.ss.usermodel.Cell c = hdr.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(bold);
            }

            BigDecimal sumD = BigDecimal.ZERO, sumC = BigDecimal.ZERO;
            if (entry.getLines() != null) {
                for (JournalEntryLine l : entry.getLines()) {
                    Row row = sh.createRow(r++);
                    String pucCode = l.getAccountingAccount() != null && l.getAccountingAccount().getPucAccount() != null
                            ? l.getAccountingAccount().getPucAccount().getCode() : "-";
                    String pucName = l.getAccountingAccount() != null && l.getAccountingAccount().getPucAccount() != null
                            ? l.getAccountingAccount().getPucAccount().getName() : "";
                    row.createCell(0).setCellValue(pucCode);
                    row.createCell(1).setCellValue(pucName);
                    row.createCell(2).setCellValue(safe(l.getDescription(), ""));
                    row.createCell(3).setCellValue(safe(l.getThirdPartyNit(), ""));
                    row.createCell(4).setCellValue(l.getCostCenter() != null ? l.getCostCenter().getName() : "");
                    row.createCell(5).setCellValue(l.getDebitAmount() != null ? l.getDebitAmount().doubleValue() : 0);
                    row.createCell(6).setCellValue(l.getCreditAmount() != null ? l.getCreditAmount().doubleValue() : 0);
                    if (l.getDebitAmount() != null) sumD = sumD.add(l.getDebitAmount());
                    if (l.getCreditAmount() != null) sumC = sumC.add(l.getCreditAmount());
                }
            }
            Row totals = sh.createRow(r++);
            totals.createCell(4).setCellValue("TOTALES");
            totals.createCell(5).setCellValue(sumD.doubleValue());
            totals.createCell(6).setCellValue(sumC.doubleValue());
            totals.getCell(4).setCellStyle(bold);
            totals.getCell(5).setCellStyle(bold);
            totals.getCell(6).setCellStyle(bold);

            for (int i = 0; i < cols.length; i++) sh.autoSizeColumn(i);

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

    private static Cell headCell(String text) {
        return new Cell().add(new Paragraph(text).setBold().setFontSize(9))
                .setBackgroundColor(HEADER_BG);
    }

    private static Cell valueCell(String text) {
        return new Cell().add(new Paragraph(text).setFontSize(9));
    }
}

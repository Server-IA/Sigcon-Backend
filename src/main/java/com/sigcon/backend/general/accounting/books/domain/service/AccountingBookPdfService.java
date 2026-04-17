package com.sigcon.backend.general.accounting.books.domain.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import com.sigcon.backend.general.accounting.books.application.AuxiliarCuentaDTO;
import com.sigcon.backend.general.accounting.books.application.BalanceComprobacionDTO;
import com.sigcon.backend.general.accounting.books.application.LibroDiarioDTO;
import com.sigcon.backend.general.accounting.books.application.LibroMayorDTO;
import com.sigcon.backend.parametrization.parameters.domain.service.SystemInfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servicio generador de PDFs para los libros contables oficiales.
 *
 * <p>Cubre las Historias de Usuario HU-CG-22 (Libro Diario y Libro Mayor en PDF),
 * HU-CG-25 (Balance de Comprobacion en PDF) y HU-CG-26 (Auxiliares por Cuenta en PDF).</p>
 *
 * <p>Utiliza iText7 directamente con tablas formateadas y usa {@link SystemInfoService}
 * para incluir en el encabezado los datos de la empresa (razon social, NIT, etc.).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingBookPdfService {

    private final AccountingBookService accountingBookService;
    private final SystemInfoService systemInfoService;

    // Paleta de marca (coherente con PdfTemplateBuilder existente)
    private static final DeviceRgb BRAND_PRIMARY = new DeviceRgb(30, 58, 138);
    private static final DeviceRgb BRAND_SECONDARY = new DeviceRgb(99, 102, 241);
    private static final DeviceRgb HEADER_BG = new DeviceRgb(238, 242, 255);
    private static final DeviceRgb ROW_ALT = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(17, 24, 39);
    private static final DeviceRgb SUBTLE = new DeviceRgb(107, 114, 128);

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] MESES = {
            "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    // =============================================================================
    // Libro Diario PDF — HU-CG-22
    // =============================================================================

    /**
     * Genera el PDF del Libro Diario para el periodo indicado.
     * Cada asiento se lista con su cabecera y sus lineas de detalle.
     *
     * @param year  anio del periodo
     * @param month mes del periodo (1-12)
     * @return bytes del PDF generado
     */
    public byte[] generateLibroDiarioPdf(Integer year, Integer month) {
        log.info("Generando PDF Libro Diario para periodo {}-{}", year, month);
        List<LibroDiarioDTO> data = accountingBookService.buildLibroDiario(year, month);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4.rotate())) {

            PageNumberHandler pn = new PageNumberHandler();
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, pn);

            doc.setMargins(35, 30, 45, 30);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            buildCompanyHeader(doc, "LIBRO DIARIO", year, month, bold, regular);

            // Tabla: Fecha | # | Descripcion | Cuenta | Nombre cuenta | Debito | Credito
            Table table = new Table(UnitValue.createPercentArray(
                    new float[]{10f, 8f, 25f, 10f, 22f, 12.5f, 12.5f}))
                    .setWidth(UnitValue.createPercentValue(100));

            addHeaderRow(table, bold, "Fecha", "# Comp.", "Descripcion", "Cuenta",
                    "Nombre Cuenta", "Debito", "Credito");

            BigDecimal totDebit = BigDecimal.ZERO;
            BigDecimal totCredit = BigDecimal.ZERO;
            boolean alt = false;

            if (data.isEmpty()) {
                addEmptyRow(table, regular, 7, "Sin asientos contabilizados para el periodo.");
            }

            for (LibroDiarioDTO entry : data) {
                String fecha = entry.getDate() != null ? entry.getDate().format(DATE_FMT) : "";
                String num = entry.getEntryNumber() != null ? entry.getEntryNumber().toString() : "";
                String desc = nvl(entry.getDescription());

                if (entry.getLines() == null || entry.getLines().isEmpty()) {
                    addDataRow(table, regular, alt, fecha, num, desc, "", "", "", "");
                    alt = !alt;
                    continue;
                }
                boolean first = true;
                for (LibroDiarioDTO.LibroDiarioLineDTO l : entry.getLines()) {
                    addDataRow(table, regular, alt,
                            first ? fecha : "",
                            first ? num : "",
                            first ? desc : "",
                            nvl(l.getAccountCode()),
                            nvl(l.getAccountName()),
                            money(l.getDebitAmount()),
                            money(l.getCreditAmount()));
                    totDebit = totDebit.add(nzd(l.getDebitAmount()));
                    totCredit = totCredit.add(nzd(l.getCreditAmount()));
                    first = false;
                }
                alt = !alt;
            }

            // Fila totales
            addTotalsRow(table, bold, new String[]{"", "", "", "", "TOTAL"},
                    new String[]{money(totDebit), money(totCredit)});

            doc.add(table);
            addFooter(doc, bold, regular, totDebit, totCredit);
        } catch (Exception e) {
            log.error("Error generando PDF Libro Diario: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando PDF del Libro Diario", e);
        }
        return baos.toByteArray();
    }

    // =============================================================================
    // Libro Mayor PDF — HU-CG-22
    // =============================================================================

    /**
     * Genera el PDF del Libro Mayor para el periodo indicado.
     * Si accountId es null, lista todas las cuentas; si no, filtra a esa cuenta.
     *
     * @param year      anio del periodo
     * @param month     mes del periodo (1-12)
     * @param accountId identificador de cuenta (opcional)
     * @return bytes del PDF generado
     */
    public byte[] generateLibroMayorPdf(Integer year, Integer month, Long accountId) {
        log.info("Generando PDF Libro Mayor para periodo {}-{}, cuenta: {}",
                year, month, accountId != null ? accountId : "TODAS");
        List<LibroMayorDTO> data = accountingBookService.buildLibroMayor(year, month, accountId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {

            PageNumberHandler pn = new PageNumberHandler();
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, pn);

            doc.setMargins(35, 30, 45, 30);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            buildCompanyHeader(doc, "LIBRO MAYOR", year, month, bold, regular);

            // Codigo | Nombre | Debitos | Creditos | Saldo
            Table table = new Table(UnitValue.createPercentArray(
                    new float[]{12f, 38f, 16f, 16f, 18f}))
                    .setWidth(UnitValue.createPercentValue(100));

            addHeaderRow(table, bold, "Codigo", "Cuenta", "Debitos", "Creditos", "Saldo");

            BigDecimal totDebit = BigDecimal.ZERO;
            BigDecimal totCredit = BigDecimal.ZERO;
            boolean alt = false;

            if (data.isEmpty()) {
                addEmptyRow(table, regular, 5, "Sin movimientos en el periodo.");
            }

            for (LibroMayorDTO row : data) {
                addDataRow(table, regular, alt,
                        nvl(row.getPucCode()),
                        nvl(row.getAccountName()),
                        money(row.getTotalDebit()),
                        money(row.getTotalCredit()),
                        money(row.getBalance()));
                totDebit = totDebit.add(nzd(row.getTotalDebit()));
                totCredit = totCredit.add(nzd(row.getTotalCredit()));
                alt = !alt;
            }
            addTotalsRow(table, bold,
                    new String[]{"", "TOTALES"},
                    new String[]{money(totDebit), money(totCredit), money(totDebit.subtract(totCredit))});

            doc.add(table);
            addFooter(doc, bold, regular, totDebit, totCredit);
        } catch (Exception e) {
            log.error("Error generando PDF Libro Mayor: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando PDF del Libro Mayor", e);
        }
        return baos.toByteArray();
    }

    // =============================================================================
    // Balance de Comprobacion PDF — HU-CG-25
    // =============================================================================

    /**
     * Genera el PDF del Balance de Comprobacion para el periodo indicado.
     * Incluye saldo inicial (Db/Cr), movimientos (Db/Cr) y saldo final (Db/Cr).
     *
     * @param year  anio del periodo
     * @param month mes del periodo (1-12)
     * @return bytes del PDF generado
     */
    public byte[] generateBalanceComprobacionPdf(Integer year, Integer month) {
        log.info("Generando PDF Balance de Comprobacion para periodo {}-{}", year, month);
        List<BalanceComprobacionDTO> data = accountingBookService.buildBalanceComprobacion(year, month);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4.rotate())) {

            PageNumberHandler pn = new PageNumberHandler();
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, pn);

            doc.setMargins(35, 25, 45, 25);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            buildCompanyHeader(doc, "BALANCE DE COMPROBACION", year, month, bold, regular);

            // Codigo | Cuenta | SI Db | SI Cr | Mov Db | Mov Cr | SF Db | SF Cr
            Table table = new Table(UnitValue.createPercentArray(
                    new float[]{8f, 24f, 11f, 11f, 11f, 11f, 12f, 12f}))
                    .setWidth(UnitValue.createPercentValue(100));

            // Cabecera agrupada: fila 1 con grupos (colspan via constructor), fila 2 con sub-columnas
            Cell hCodigo = new Cell(2, 1).setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BRAND_SECONDARY, 0.5f)).setPadding(4)
                    .add(new Paragraph("Codigo").setFont(bold).setFontSize(8).setFontColor(BRAND_PRIMARY));
            Cell hCuenta = new Cell(2, 1).setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BRAND_SECONDARY, 0.5f)).setPadding(4)
                    .add(new Paragraph("Cuenta").setFont(bold).setFontSize(8).setFontColor(BRAND_PRIMARY));
            Cell hSi = new Cell(1, 2).setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BRAND_SECONDARY, 0.5f)).setPadding(4)
                    .setTextAlignment(TextAlignment.CENTER)
                    .add(new Paragraph("Saldo Inicial").setFont(bold).setFontSize(8).setFontColor(BRAND_PRIMARY));
            Cell hMv = new Cell(1, 2).setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BRAND_SECONDARY, 0.5f)).setPadding(4)
                    .setTextAlignment(TextAlignment.CENTER)
                    .add(new Paragraph("Movimientos").setFont(bold).setFontSize(8).setFontColor(BRAND_PRIMARY));
            Cell hSf = new Cell(1, 2).setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BRAND_SECONDARY, 0.5f)).setPadding(4)
                    .setTextAlignment(TextAlignment.CENTER)
                    .add(new Paragraph("Saldo Final").setFont(bold).setFontSize(8).setFontColor(BRAND_PRIMARY));
            table.addHeaderCell(hCodigo);
            table.addHeaderCell(hCuenta);
            table.addHeaderCell(hSi);
            table.addHeaderCell(hMv);
            table.addHeaderCell(hSf);
            table.addHeaderCell(headerCell("Debito", bold).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(headerCell("Credito", bold).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(headerCell("Debito", bold).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(headerCell("Credito", bold).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(headerCell("Debito", bold).setTextAlignment(TextAlignment.RIGHT));
            table.addHeaderCell(headerCell("Credito", bold).setTextAlignment(TextAlignment.RIGHT));

            BigDecimal tSiD = BigDecimal.ZERO, tSiC = BigDecimal.ZERO;
            BigDecimal tMvD = BigDecimal.ZERO, tMvC = BigDecimal.ZERO;
            BigDecimal tSfD = BigDecimal.ZERO, tSfC = BigDecimal.ZERO;
            boolean alt = false;

            if (data.isEmpty()) {
                addEmptyRow(table, regular, 8, "Sin cuentas con movimientos en el periodo.");
            }

            for (BalanceComprobacionDTO r : data) {
                addDataRow(table, regular, alt,
                        nvl(r.getPucCode()),
                        nvl(r.getAccountName()),
                        money(r.getSaldoAnteriorDebit()),
                        money(r.getSaldoAnteriorCredit()),
                        money(r.getMovimientoDebit()),
                        money(r.getMovimientoCredit()),
                        money(r.getSaldoFinalDebit()),
                        money(r.getSaldoFinalCredit()));
                tSiD = tSiD.add(nzd(r.getSaldoAnteriorDebit()));
                tSiC = tSiC.add(nzd(r.getSaldoAnteriorCredit()));
                tMvD = tMvD.add(nzd(r.getMovimientoDebit()));
                tMvC = tMvC.add(nzd(r.getMovimientoCredit()));
                tSfD = tSfD.add(nzd(r.getSaldoFinalDebit()));
                tSfC = tSfC.add(nzd(r.getSaldoFinalCredit()));
                alt = !alt;
            }

            addTotalsRow(table, bold,
                    new String[]{"", "TOTALES"},
                    new String[]{money(tSiD), money(tSiC), money(tMvD), money(tMvC),
                                 money(tSfD), money(tSfC)});

            doc.add(table);
            addFooter(doc, bold, regular, tMvD, tMvC);
        } catch (Exception e) {
            log.error("Error generando PDF Balance Comprobacion: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando PDF del Balance de Comprobacion", e);
        }
        return baos.toByteArray();
    }

    // =============================================================================
    // Auxiliares por Cuenta PDF — HU-CG-26
    // =============================================================================

    /**
     * Genera el PDF del Auxiliar por Cuenta para el periodo indicado.
     *
     * @param year      anio del periodo
     * @param month     mes del periodo (1-12)
     * @param accountId identificador de cuenta contable (obligatorio)
     * @return bytes del PDF generado
     */
    public byte[] generateAuxiliaresPdf(Integer year, Integer month, Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId es obligatorio para el Auxiliar por Cuenta.");
        }
        log.info("Generando PDF Auxiliar por Cuenta {} para periodo {}-{}", accountId, year, month);
        List<AuxiliarCuentaDTO> data = accountingBookService.buildAuxiliaresCuentas(year, month, accountId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {

            PageNumberHandler pn = new PageNumberHandler();
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, pn);

            doc.setMargins(35, 30, 45, 30);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            buildCompanyHeader(doc, "AUXILIAR POR CUENTA (ID " + accountId + ")", year, month, bold, regular);

            // Fecha | Comprobante | Descripcion | Debito | Credito | Saldo
            Table table = new Table(UnitValue.createPercentArray(
                    new float[]{12f, 12f, 32f, 14f, 14f, 16f}))
                    .setWidth(UnitValue.createPercentValue(100));

            addHeaderRow(table, bold, "Fecha", "Comprobante", "Descripcion",
                    "Debito", "Credito", "Saldo Acum.");

            BigDecimal totDebit = BigDecimal.ZERO;
            BigDecimal totCredit = BigDecimal.ZERO;
            boolean alt = false;

            if (data.isEmpty()) {
                addEmptyRow(table, regular, 6, "Sin movimientos para la cuenta en el periodo.");
            }

            for (AuxiliarCuentaDTO r : data) {
                addDataRow(table, regular, alt,
                        r.getDate() != null ? r.getDate().format(DATE_FMT) : "",
                        r.getEntryNumber() != null ? r.getEntryNumber().toString() : "",
                        nvl(r.getDescription()),
                        money(r.getDebit()),
                        money(r.getCredit()),
                        money(r.getRunningBalance()));
                totDebit = totDebit.add(nzd(r.getDebit()));
                totCredit = totCredit.add(nzd(r.getCredit()));
                alt = !alt;
            }

            addTotalsRow(table, bold,
                    new String[]{"", "", "TOTALES"},
                    new String[]{money(totDebit), money(totCredit), ""});

            doc.add(table);
            addFooter(doc, bold, regular, totDebit, totCredit);
        } catch (Exception e) {
            log.error("Error generando PDF Auxiliar por Cuenta: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando PDF del Auxiliar por Cuenta", e);
        }
        return baos.toByteArray();
    }

    // =============================================================================
    // Helpers de construccion de PDF
    // =============================================================================

    /**
     * Agrega el encabezado institucional: banda de marca + razon social + NIT +
     * direccion + titulo del libro + periodo.
     */
    private void buildCompanyHeader(Document doc, String bookTitle, Integer year, Integer month,
                                    PdfFont bold, PdfFont regular) {
        Map<String, String> info = systemInfoService.getSystemInfo();
        String name = orDefault(info.get("COMPANY_NAME"), "SIGCON");
        String nit = orDefault(info.get("COMPANY_NIT"), "");
        String dv = info.get("COMPANY_DV");
        if (dv != null && !dv.isBlank() && !nit.isBlank()) {
            nit = nit + "-" + dv;
        }
        String direccion = orDefault(info.get("COMPANY_ADDRESS"), "");
        String periodo = (month != null && month >= 1 && month <= 12 ? MESES[month] : "") + " " + year;

        // Banda superior
        Table band = new Table(UnitValue.createPercentArray(new float[]{100f}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(BRAND_PRIMARY);
        Cell bc = new Cell().setBorder(Border.NO_BORDER).setPadding(10);
        bc.add(new Paragraph(name).setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE));
        if (!nit.isBlank()) {
            bc.add(new Paragraph("NIT: " + nit).setFont(regular).setFontSize(9)
                    .setFontColor(new DeviceRgb(196, 204, 255)));
        }
        if (!direccion.isBlank()) {
            bc.add(new Paragraph(direccion).setFont(regular).setFontSize(9)
                    .setFontColor(new DeviceRgb(196, 204, 255)));
        }
        band.addCell(bc);
        doc.add(band);

        // Barra de titulo
        Table titleBar = new Table(UnitValue.createPercentArray(new float[]{70f, 30f}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(BRAND_SECONDARY)
                .setMarginBottom(10);
        titleBar.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(8)
                .add(new Paragraph(bookTitle).setFont(bold).setFontSize(12)
                        .setFontColor(ColorConstants.WHITE)));
        titleBar.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(8)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph("Periodo: " + periodo).setFont(bold).setFontSize(10)
                        .setFontColor(ColorConstants.WHITE)));
        doc.add(titleBar);

        // Linea meta
        doc.add(new Paragraph("Generado: " + LocalDateTime.now().format(TIMESTAMP_FMT))
                .setFont(regular).setFontSize(8).setFontColor(SUBTLE)
                .setTextAlignment(TextAlignment.RIGHT).setMarginBottom(6));
    }

    /**
     * Agrega el pie de pagina con totales cuadratura (Debito vs Credito) + timestamp.
     * El numero de pagina se renderiza via {@link PageNumberHandler}.
     */
    private void addFooter(Document doc, PdfFont bold, PdfFont regular,
                           BigDecimal totDebit, BigDecimal totCredit) {
        BigDecimal diff = nzd(totDebit).subtract(nzd(totCredit));
        String cuadra = diff.compareTo(BigDecimal.ZERO) == 0
                ? "Cuadratura OK (Debitos = Creditos)"
                : "Diferencia: " + money(diff);

        doc.add(new Paragraph().setBorderTop(new SolidBorder(SUBTLE, 0.5f))
                .setMarginTop(14).setMarginBottom(4));
        doc.add(new Paragraph("Total Debitos: " + money(totDebit)
                + "   |   Total Creditos: " + money(totCredit)
                + "   |   " + cuadra)
                .setFont(bold).setFontSize(8).setFontColor(TEXT_DARK)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("Documento generado automaticamente por SIGCON — "
                + LocalDateTime.now().format(TIMESTAMP_FMT))
                .setFont(regular).setFontSize(7).setFontColor(SUBTLE)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private void addHeaderRow(Table table, PdfFont bold, String... headers) {
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerCell(headers[i], bold);
            // Alinear montos a la derecha (ultimos N campos por convencion)
            if (isMoneyHeader(headers[i])) c.setTextAlignment(TextAlignment.RIGHT);
            table.addHeaderCell(c);
        }
    }

    private Cell headerCell(String text, PdfFont bold) {
        return new Cell()
                .setBackgroundColor(HEADER_BG)
                .setBorder(new SolidBorder(BRAND_SECONDARY, 0.5f))
                .setPadding(4)
                .add(new Paragraph(text).setFont(bold).setFontSize(8).setFontColor(BRAND_PRIMARY));
    }

    private boolean isMoneyHeader(String h) {
        return h != null && (h.toLowerCase(Locale.ROOT).contains("debito")
                || h.toLowerCase(Locale.ROOT).contains("credito")
                || h.toLowerCase(Locale.ROOT).contains("saldo"));
    }

    private void addDataRow(Table table, PdfFont regular, boolean alt, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            String v = cells[i];
            boolean moneyCol = isMoneyValue(v);
            Cell c = new Cell()
                    .setBorder(new SolidBorder(new DeviceRgb(229, 231, 235), 0.3f))
                    .setPadding(3)
                    .add(new Paragraph(v == null ? "" : v).setFont(regular).setFontSize(7.5f)
                            .setFontColor(TEXT_DARK));
            if (alt) c.setBackgroundColor(ROW_ALT);
            if (moneyCol) c.setTextAlignment(TextAlignment.RIGHT);
            table.addCell(c);
        }
    }

    private boolean isMoneyValue(String v) {
        if (v == null || v.isBlank()) return false;
        // Rough: cadenas que empiezan con digito/signo y contienen separador decimal o miles
        return v.matches("-?[0-9.,]+");
    }

    private void addEmptyRow(Table table, PdfFont regular, int colspan, String msg) {
        Cell c = new Cell(1, colspan)
                .setBorder(new SolidBorder(new DeviceRgb(229, 231, 235), 0.3f))
                .setPadding(10)
                .setTextAlignment(TextAlignment.CENTER)
                .add(new Paragraph(msg).setFont(regular).setFontSize(9).setFontColor(SUBTLE).setItalic());
        table.addCell(c);
    }

    private void addTotalsRow(Table table, PdfFont bold, String[] labelCells, String[] amountCells) {
        for (String l : labelCells) {
            Cell c = new Cell().setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BRAND_SECONDARY, 0.5f))
                    .setPadding(4)
                    .add(new Paragraph(l == null ? "" : l).setFont(bold).setFontSize(8)
                            .setFontColor(BRAND_PRIMARY));
            table.addCell(c);
        }
        for (String a : amountCells) {
            Cell c = new Cell().setBackgroundColor(HEADER_BG)
                    .setBorder(new SolidBorder(BRAND_SECONDARY, 0.5f))
                    .setPadding(4)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(a == null ? "" : a).setFont(bold).setFontSize(8)
                            .setFontColor(BRAND_PRIMARY));
            table.addCell(c);
        }
    }

    // =============================================================================
    // Utilidades de formato
    // =============================================================================

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String orDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private static BigDecimal nzd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Formatea un BigDecimal como moneda colombiana con separadores de miles
     * y 2 decimales. Si es null o cero, retorna cadena vacia para celdas limpias.
     */
    private static String money(BigDecimal v) {
        if (v == null) return "";
        BigDecimal rounded = v.setScale(2, RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.ZERO) == 0) return "0.00";
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setDecimalSeparator('.');
        sym.setGroupingSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", sym);
        return df.format(rounded);
    }

    // =============================================================================
    // Event handler para numero de pagina ("Pagina X")
    // =============================================================================

    /**
     * Handler que renderiza "Pagina X" en el pie de pagina de cada hoja del PDF.
     * No incluye el total (no conocido durante el primer pase); agrega solo el numero actual.
     */
    private static class PageNumberHandler implements com.itextpdf.kernel.events.IEventHandler {
        @Override
        public void handleEvent(com.itextpdf.kernel.events.Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdf = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            int pageNumber = pdf.getPageNumber(page);
            try {
                PdfFont f = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                PdfCanvas canvas = new PdfCanvas(page);
                canvas.beginText()
                        .setFontAndSize(f, 7)
                        .setFillColor(SUBTLE)
                        .moveText(page.getPageSize().getWidth() - 60,
                                page.getPageSize().getBottom() + 20)
                        .showText("Pagina " + pageNumber)
                        .endText();
            } catch (Exception e) {
                // Sin detener el flujo si falla el numero de pagina
            }
        }
    }
}

package com.sigcon.backend.general.accounting.statements.domain.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
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
import com.sigcon.backend.general.accounting.statements.application.BalanceGeneralDTO;
import com.sigcon.backend.general.accounting.statements.application.EstadoPatrimonioDTO;
import com.sigcon.backend.general.accounting.statements.application.EstadoResultadosDTO;
import com.sigcon.backend.general.accounting.statements.application.FlujoEfectivoDTO;
import com.sigcon.backend.utils.export.ReportContextResolver;
import com.sigcon.backend.utils.export.ReportHeaderBuilder;
import com.sigcon.backend.utils.export.SimpleTableExporter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * QA Bloque BP (2026-05-18, HU-CG-09/10/11/16/18): exportacion de estados
 * financieros a PDF/Excel/CSV cubriendo los escenarios E5 de HU-CG-09 y
 * HU-CG-10 (falta exportacion) y los escenarios equivalentes en HU-CG-11
 * (Flujo de Efectivo) y HU-CG-18 (Estado de Cambios en el Patrimonio).
 *
 * <p>Reutiliza {@link FinancialStatementService} para construir el contenido
 * y {@link SimpleTableExporter} para CSV/XLSX. El PDF se arma con iText 7
 * en formato vertical A4 con encabezado de empresa + filtros + tabla.</p>
 *
 * <p>Cada export emite un evento {@code EXPORT} en {@link AuditPublisher}
 * cubriendo el escenario E6 de auditoria de generacion de reportes (HU-CG-09 E6,
 * HU-CG-10 E6, HU-CG-11 E5).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialStatementExportService {

    private final FinancialStatementService financialStatementService;
    private final ReportContextResolver reportContextResolver;
    private final AuditPublisher auditPublisher;

    /** MIME PDF. */
    public static final String PDF_MIME = "application/pdf";

    /** Resultado de un export. */
    public static final class ExportResult {
        public final byte[] content;
        public final String fileName;
        public final String mime;
        public ExportResult(byte[] content, String fileName, String mime) {
            this.content = content;
            this.fileName = fileName;
            this.mime = mime;
        }
    }

    /**
     * Exporta el Balance General del periodo en el formato indicado.
     * Formatos soportados: pdf, xlsx, csv.
     */
    public ExportResult exportBalanceGeneral(Integer year, Integer month, String format) {
        ResponseEntity<?> resp = financialStatementService.getBalanceGeneral(year, month);
        BalanceGeneralDTO data = extractData(resp, BalanceGeneralDTO.class);
        String label = labelPeriodo(year, month);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Balance General - " + label)
                .addFilter("Periodo", label)
                .addTotal("Total Activos", data.getTotalActivos())
                .addTotal("Total Pasivos", data.getTotalPasivos())
                .addTotal("Total Patrimonio", data.getTotalPatrimonio())
                .addTotal("Ecuacion contable",
                        BigDecimal.valueOf(Boolean.TRUE.equals(data.getIsBalanced()) ? 1 : 0))
                .build();

        List<ClassRow> rows = new ArrayList<>();
        if (data.getDetails() != null) {
            for (BalanceGeneralDTO.ClassDetailDTO section : data.getDetails()) {
                if (section.getAccounts() != null) {
                    for (BalanceGeneralDTO.AccountDetailDTO a : section.getAccounts()) {
                        rows.add(new ClassRow(section.getClassName(),
                                a.getPucCode(), a.getAccountName(), a.getBalance()));
                    }
                }
            }
        }
        List<Object> totalsRow = List.of("TOTAL", "(" + rows.size() + " cuentas)", "",
                nz(data.getTotalActivos())
                        .add(nz(data.getTotalPasivos()))
                        .add(nz(data.getTotalPatrimonio())));

        List<String> headers = List.of("Clase", "Codigo PUC", "Cuenta", "Saldo");
        List<Function<ClassRow, Object>> cols = new ArrayList<>();
        cols.add(r -> r.section);
        cols.add(r -> r.code);
        cols.add(r -> r.name);
        cols.add(r -> r.balance != null ? r.balance.doubleValue() : 0d);

        ExportResult result = encode("BalanceGeneral", label, format,
                headers, cols, rows, ctx, totalsRow);
        publishExportAudit("BalanceGeneral", year, month, format, rows.size());
        return result;
    }

    /** Exporta el Estado de Resultados del periodo. */
    public ExportResult exportEstadoResultados(Integer year, Integer month, String format) {
        ResponseEntity<?> resp = financialStatementService.getEstadoResultados(year, month);
        EstadoResultadosDTO data = extractData(resp, EstadoResultadosDTO.class);
        String label = labelPeriodo(year, month);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Estado de Resultados - " + label)
                .addFilter("Periodo", label)
                .addTotal("Total Ingresos", data.getTotalIngresos())
                .addTotal("Total Gastos", data.getTotalGastos())
                .addTotal("Total Costos", data.getTotalCostos())
                .addTotal("Utilidad Bruta", data.getUtilidadBruta())
                .addTotal("Utilidad Neta", data.getUtilidadNeta())
                .build();

        List<ClassRow> rows = new ArrayList<>();
        if (data.getDetails() != null) {
            for (BalanceGeneralDTO.ClassDetailDTO section : data.getDetails()) {
                if (section.getAccounts() != null) {
                    for (BalanceGeneralDTO.AccountDetailDTO a : section.getAccounts()) {
                        rows.add(new ClassRow(section.getClassName(),
                                a.getPucCode(), a.getAccountName(), a.getBalance()));
                    }
                }
            }
        }
        List<Object> totalsRow = List.of("TOTAL", "(" + rows.size() + " cuentas)", "",
                nz(data.getUtilidadNeta()));

        List<String> headers = List.of("Clase", "Codigo PUC", "Cuenta", "Saldo");
        List<Function<ClassRow, Object>> cols = new ArrayList<>();
        cols.add(r -> r.section);
        cols.add(r -> r.code);
        cols.add(r -> r.name);
        cols.add(r -> r.balance != null ? r.balance.doubleValue() : 0d);

        ExportResult result = encode("EstadoResultados", label, format,
                headers, cols, rows, ctx, totalsRow);
        publishExportAudit("EstadoResultados", year, month, format, rows.size());
        return result;
    }

    /** Exporta el Flujo de Efectivo NIC 7 del periodo. */
    public ExportResult exportFlujoEfectivo(Integer year, Integer month, String format) {
        ResponseEntity<?> resp = financialStatementService.getFlujoEfectivo(year, month);
        FlujoEfectivoDTO data = extractData(resp, FlujoEfectivoDTO.class);
        String label = labelPeriodo(year, month);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Flujo de Efectivo - " + label)
                .addFilter("Periodo", label)
                .addTotal("Flujo Operativo", data.getFlujoOperativo())
                .addTotal("Flujo Inversion", data.getFlujoInversion())
                .addTotal("Flujo Financiacion", data.getFlujoFinanciacion())
                .addTotal("Flujo Neto", data.getFlujoNeto())
                .build();

        List<FlowRow> rows = new ArrayList<>();
        if (data.getDetails() != null) {
            for (FlujoEfectivoDTO.ActivityDetailDTO act : data.getDetails()) {
                if (act.getEntries() != null) {
                    for (FlujoEfectivoDTO.EntryDetailDTO e : act.getEntries()) {
                        rows.add(new FlowRow(act.getActivityType(), e.getEntryNumber(),
                                e.getDescription(), e.getSourceModule(),
                                e.getTotalDebit(), e.getTotalCredit()));
                    }
                }
            }
        }
        List<Object> totalsRow = List.of("TOTAL", "", "", "",
                nz(data.getFlujoNeto()), "");

        List<String> headers = List.of("Actividad", "Asiento", "Descripcion",
                "Origen", "Debito", "Credito");
        List<Function<FlowRow, Object>> cols = new ArrayList<>();
        cols.add(r -> r.activity);
        cols.add(r -> r.entryNumber);
        cols.add(r -> r.description);
        cols.add(r -> sourceModuleLabelEs(r.sourceModule));
        cols.add(r -> r.debit != null ? r.debit.doubleValue() : 0d);
        cols.add(r -> r.credit != null ? r.credit.doubleValue() : 0d);

        ExportResult result = encode("FlujoEfectivo", label, format,
                headers, cols, rows, ctx, totalsRow);
        publishExportAudit("FlujoEfectivo", year, month, format, rows.size());
        return result;
    }

    /** Exporta el Estado de Cambios en el Patrimonio (HU-CG-18). */
    public ExportResult exportCambiosPatrimonio(Integer year, Integer month, String format) {
        ResponseEntity<?> resp = financialStatementService.getEstadoCambiosPatrimonio(year, month);
        EstadoPatrimonioDTO data = extractData(resp, EstadoPatrimonioDTO.class);
        String label = labelPeriodo(year, month);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Estado de Cambios en el Patrimonio - " + label)
                .addFilter("Periodo", label)
                .addTotal("Saldo Inicial", data.getSaldoInicial())
                .addTotal("Aportes", data.getAportes())
                .addTotal("Utilidad Neta", data.getUtilidadNeta())
                .addTotal("Reservas", data.getReservas())
                .addTotal("Dividendos", data.getDividendosDecretados())
                .addTotal("Saldo Final", data.getSaldoFinal())
                .build();

        List<EquityRow> rows = new ArrayList<>();
        if (data.getDetails() != null) {
            for (EstadoPatrimonioDTO.AccountMovementDTO d : data.getDetails()) {
                rows.add(new EquityRow(d.getPucCode(), d.getAccountName(),
                        d.getSaldoInicial(), d.getMovimientosDebito(),
                        d.getMovimientosCredito(), d.getSaldoFinal()));
            }
        }
        List<Object> totalsRow = List.of("TOTAL",
                "(" + rows.size() + " cuentas)",
                nz(data.getSaldoInicial()),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                nz(data.getSaldoFinal()));

        List<String> headers = List.of("Codigo PUC", "Cuenta", "Saldo Inicial",
                "Movimientos Debito", "Movimientos Credito", "Saldo Final");
        List<Function<EquityRow, Object>> cols = new ArrayList<>();
        cols.add(r -> r.code);
        cols.add(r -> r.name);
        cols.add(r -> nz(r.saldoInicial).doubleValue());
        cols.add(r -> nz(r.movimientosDebito).doubleValue());
        cols.add(r -> nz(r.movimientosCredito).doubleValue());
        cols.add(r -> nz(r.saldoFinal).doubleValue());

        ExportResult result = encode("CambiosPatrimonio", label, format,
                headers, cols, rows, ctx, totalsRow);
        publishExportAudit("CambiosPatrimonio", year, month, format, rows.size());
        return result;
    }

    /** Exporta el Balance Comparativo entre dos periodos (HU-CG-13). */
    @SuppressWarnings("unchecked")
    public ExportResult exportComparativo(Integer year1, Integer month1,
                                            Integer year2, Integer month2, String format) {
        ResponseEntity<?> resp = financialStatementService.getComparativo(year1, month1, year2, month2);
        Object data = extractRawData(resp);
        if (!(data instanceof List)) {
            throw new IllegalStateException("Respuesta de comparativo no contiene lista de filas");
        }
        List<java.util.Map<String, Object>> list = (List<java.util.Map<String, Object>>) data;

        String labelA = labelPeriodo(year1, month1);
        String labelB = labelPeriodo(year2, month2);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Balance Comparativo " + labelA + " vs " + labelB)
                .addFilter("Periodo A", labelA)
                .addFilter("Periodo B", labelB)
                .build();

        List<String> headers = List.of("Clase", "Periodo A", "Saldo A",
                "Periodo B", "Saldo B", "Variacion Absoluta", "Variacion %");
        List<Function<java.util.Map<String, Object>, Object>> cols = new ArrayList<>();
        cols.add(m -> m.get("className"));
        cols.add(m -> m.get("period1Label"));
        cols.add(m -> num(m.get("period1Value")));
        cols.add(m -> m.get("period2Label"));
        cols.add(m -> num(m.get("period2Value")));
        cols.add(m -> num(m.get("variacionAbsoluta")));
        cols.add(m -> num(m.get("variacionPorcentual")));

        ExportResult result = encode("Comparativo", labelA + "-vs-" + labelB,
                format, headers, cols, list, ctx, null);
        publishExportAudit("Comparativo", year1, month1, format, list.size());
        return result;
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private static String labelPeriodo(Integer year, Integer month) {
        if (year == null || month == null) return "?";
        return year + "-" + String.format("%02d", month);
    }

    @SuppressWarnings("unchecked")
    private <T> T extractData(ResponseEntity<?> resp, Class<T> klass) {
        if (resp == null || resp.getBody() == null) {
            throw new IllegalStateException("Respuesta vacia");
        }
        Object body = resp.getBody();
        if (body instanceof java.util.Map) {
            Object data = ((java.util.Map<String, Object>) body).get("data");
            if (data == null) {
                throw new IllegalStateException("Respuesta sin 'data'");
            }
            if (klass.isInstance(data)) return (T) data;
            throw new IllegalStateException(
                    "Tipo de data inesperado: " + data.getClass().getSimpleName());
        }
        if (klass.isInstance(body)) return (T) body;
        throw new IllegalStateException(
                "Tipo de body inesperado: " + body.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private Object extractRawData(ResponseEntity<?> resp) {
        if (resp == null || resp.getBody() == null) return null;
        Object body = resp.getBody();
        if (body instanceof java.util.Map) {
            return ((java.util.Map<String, Object>) body).get("data");
        }
        return body;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static double num(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0d;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0d; }
    }

    /**
     * QA Bloque BP: mapeo del codigo de modulo origen a etiqueta humana en
     * espanol, para que en CSV/XLSX/PDF no salgan los literales AP/AR/BNK.
     */
    private static String sourceModuleLabelEs(String code) {
        if (code == null) return "";
        switch (code) {
            case "CG":     return "Contabilidad General";
            case "AP":     return "Cuentas por Pagar";
            case "AR":     return "Cuentas por Cobrar";
            case "BNK":    return "Bancos y Cajas";
            case "ACT":    return "Activos";
            case "NOM":    return "Nomina";
            case "INT":    return "Integracion AAEF";
            case "MANUAL": return "Manual";
            default:       return code;
        }
    }

    private void publishExportAudit(String type, Integer year, Integer month,
                                     String format, int rows) {
        try {
            auditPublisher.publish(AuditAction.EXPORT, AuditModule.CG, AuditSeverity.LOW,
                    "FinancialStatement", null,
                    "Export " + type + " " + labelPeriodo(year, month) + " formato=" + format
                            + " filas=" + rows,
                    null, null, null);
        } catch (RuntimeException ignored) { /* audit no debe romper export */ }
    }

    private <T> ExportResult encode(String type, String period, String format,
                                     List<String> headers,
                                     List<Function<T, Object>> cols,
                                     List<T> rows,
                                     ReportHeaderBuilder.ReportContext ctx,
                                     List<Object> totalsRow) {
        String fmt = format != null ? format.toLowerCase() : "csv";
        switch (fmt) {
            case "csv": {
                byte[] data = SimpleTableExporter.toCsv(headers, cols, rows, ctx, totalsRow);
                return new ExportResult(data, type + "-" + period + ".csv",
                        SimpleTableExporter.CSV_MIME);
            }
            case "xlsx": {
                byte[] data = SimpleTableExporter.toXlsx(type, headers, cols, rows, ctx, totalsRow);
                return new ExportResult(data, type + "-" + period + ".xlsx",
                        SimpleTableExporter.XLSX_MIME);
            }
            case "pdf": {
                byte[] data = buildPdf(type, period, ctx, headers, cols, rows, totalsRow);
                return new ExportResult(data, type + "-" + period + ".pdf", PDF_MIME);
            }
            default:
                throw new IllegalArgumentException("Formato no soportado: " + format
                        + ". Use csv, xlsx o pdf.");
        }
    }

    private <T> byte[] buildPdf(String title, String period,
                                  ReportHeaderBuilder.ReportContext ctx,
                                  List<String> headers,
                                  List<Function<T, Object>> cols,
                                  List<T> rows,
                                  List<Object> totalsRow) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);
            try (Document doc = new Document(pdf)) {
                PdfFont titleFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                PdfFont bodyFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                // Encabezado con titulo
                Paragraph header = new Paragraph(title + " - " + period)
                        .setFont(titleFont).setFontSize(16)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(new DeviceRgb(30, 58, 138));
                doc.add(header);

                // Datos de empresa y filtros
                if (ctx != null) {
                    StringBuilder meta = new StringBuilder();
                    if (ctx.companyName != null) {
                        meta.append("Empresa: ").append(ctx.companyName);
                        if (ctx.companyNit != null) {
                            meta.append(" - NIT ").append(ctx.companyNit);
                        }
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
                    if (ctx.filters != null && !ctx.filters.isEmpty()) {
                        meta.append("\nFiltros: ");
                        ctx.filters.forEach((k, v) -> meta.append(k).append("=").append(v).append("; "));
                    }
                    Paragraph metaP = new Paragraph(meta.toString())
                            .setFont(bodyFont).setFontSize(9)
                            .setFontColor(new DeviceRgb(75, 85, 99));
                    doc.add(metaP);
                }

                // Tabla de datos
                Table table = new Table(UnitValue.createPercentArray(headers.size()))
                        .useAllAvailableWidth();
                for (String h : headers) {
                    table.addHeaderCell(new Cell().add(new Paragraph(h).setFont(titleFont).setFontSize(9))
                            .setBackgroundColor(new DeviceRgb(238, 242, 255)));
                }
                DecimalFormat df = new DecimalFormat("#,##0.00",
                        new DecimalFormatSymbols(new Locale("es", "CO")));
                for (T row : rows) {
                    for (Function<T, Object> col : cols) {
                        Object v = col.apply(row);
                        String text;
                        if (v == null) {
                            text = "";
                        } else if (v instanceof Number n) {
                            text = df.format(n.doubleValue());
                        } else {
                            text = v.toString();
                        }
                        table.addCell(new Cell().add(new Paragraph(text)
                                .setFont(bodyFont).setFontSize(8)));
                    }
                }
                if (totalsRow != null && !totalsRow.isEmpty()) {
                    for (Object v : totalsRow) {
                        String text;
                        if (v == null) {
                            text = "";
                        } else if (v instanceof Number n) {
                            text = df.format(n.doubleValue());
                        } else {
                            text = v.toString();
                        }
                        table.addCell(new Cell().add(new Paragraph(text)
                                .setFont(titleFont).setFontSize(9))
                                .setBackgroundColor(new DeviceRgb(217, 226, 243)));
                    }
                }
                doc.add(table);

                // Totales del header
                if (ctx != null && ctx.totals != null && !ctx.totals.isEmpty()) {
                    StringBuilder sum = new StringBuilder("\nResumen:\n");
                    ctx.totals.forEach((k, v) -> sum.append("  ").append(k)
                            .append(": ").append(v != null
                                    ? df.format(v.doubleValue())
                                    : "0.00").append('\n'));
                    doc.add(new Paragraph(sum.toString()).setFont(bodyFont).setFontSize(9));
                }

                // Footer
                Paragraph footer = new Paragraph("Documento generado automaticamente. Pagina con valor probatorio.")
                        .setFont(bodyFont).setFontSize(7)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.GRAY);
                doc.add(footer);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Error generando PDF: " + e.getMessage(), e);
        }
    }

    // Tipos auxiliares (clases con campos publicos para uso desde lambdas).
    private static final class ClassRow {
        public final String section;
        public final String code;
        public final String name;
        public final BigDecimal balance;
        ClassRow(String section, String code, String name, BigDecimal balance) {
            this.section = section; this.code = code; this.name = name; this.balance = balance;
        }
    }

    private static final class FlowRow {
        public final String activity;
        public final Long entryNumber;
        public final String description;
        public final String sourceModule;
        public final BigDecimal debit;
        public final BigDecimal credit;
        FlowRow(String activity, Long entryNumber, String description, String sourceModule,
                BigDecimal debit, BigDecimal credit) {
            this.activity = activity; this.entryNumber = entryNumber;
            this.description = description; this.sourceModule = sourceModule;
            this.debit = debit; this.credit = credit;
        }
    }

    private static final class EquityRow {
        public final String code;
        public final String name;
        public final BigDecimal saldoInicial;
        public final BigDecimal movimientosDebito;
        public final BigDecimal movimientosCredito;
        public final BigDecimal saldoFinal;
        EquityRow(String code, String name, BigDecimal saldoInicial,
                  BigDecimal movimientosDebito, BigDecimal movimientosCredito,
                  BigDecimal saldoFinal) {
            this.code = code; this.name = name;
            this.saldoInicial = saldoInicial;
            this.movimientosDebito = movimientosDebito;
            this.movimientosCredito = movimientosCredito;
            this.saldoFinal = saldoFinal;
        }
    }
}

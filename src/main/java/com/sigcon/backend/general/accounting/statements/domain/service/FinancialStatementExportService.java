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

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
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
        // HU-CG-10: presentacion financiera NIC 1 (clasificacion granular por subgrupo PUC).
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Estado de Resultados - " + label)
                .addFilter("Periodo", label)
                .addTotal("Ingresos operacionales", data.getIngresosOperacionales())
                .addTotal("(-) Costos", data.getTotalCostos())
                .addTotal("= Utilidad bruta", data.getUtilidadBrutaOperacional())
                .addTotal("(-) Gastos de administracion", data.getGastosAdministracion())
                .addTotal("(-) Gastos de ventas", data.getGastosVentas())
                .addTotal("= Utilidad operacional", data.getUtilidadOperacional())
                .addTotal("(+) Ingresos financieros", data.getIngresosFinancieros())
                .addTotal("(+) Otros ingresos", data.getOtrosIngresos())
                .addTotal("(-) Gastos financieros", data.getGastosFinancieros())
                .addTotal("(-) Otros gastos", data.getOtrosGastos())
                .addTotal("= Utilidad antes de impuestos", data.getUtilidadAntesImpuestos())
                .addTotal("(-) Impuesto de renta", data.getImpuestoRenta())
                .addTotal("= Utilidad neta", data.getUtilidadNeta())
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

    /** Overload de 2 periodos (compatibilidad). */
    public ExportResult exportComparativo(Integer year1, Integer month1,
                                            Integer year2, Integer month2, String format) {
        return exportComparativo(year1, month1, year2, month2, null, null, format);
    }

    /** Exporta el Balance Comparativo entre DOS o TRES periodos (HU-CG-13). */
    @SuppressWarnings("unchecked")
    public ExportResult exportComparativo(Integer year1, Integer month1,
                                            Integer year2, Integer month2,
                                            Integer year3, Integer month3, String format) {
        boolean tres = year3 != null && month3 != null;
        ResponseEntity<?> resp = financialStatementService.getComparativo(
                year1, month1, year2, month2, year3, month3);
        Object data = extractRawData(resp);
        if (!(data instanceof List)) {
            throw new IllegalStateException("Respuesta de comparativo no contiene lista de filas");
        }
        List<java.util.Map<String, Object>> list = (List<java.util.Map<String, Object>>) data;

        String labelA = labelPeriodo(year1, month1);
        String labelB = labelPeriodo(year2, month2);
        String labelC = tres ? labelPeriodo(year3, month3) : null;
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Balance Comparativo " + labelA + " vs " + labelB
                        + (tres ? " vs " + labelC : ""))
                .addFilter("Periodo A", labelA)
                .addFilter("Periodo B", labelB)
                .build();

        List<String> headers = new ArrayList<>(List.of("Clase", "Periodo A", "Saldo A",
                "Periodo B", "Saldo B", "Var. Abs. A-B", "Var. % A-B"));
        List<Function<java.util.Map<String, Object>, Object>> cols = new ArrayList<>();
        cols.add(m -> m.get("className"));
        cols.add(m -> m.get("period1Label"));
        cols.add(m -> num(m.get("period1Value")));
        cols.add(m -> m.get("period2Label"));
        cols.add(m -> num(m.get("period2Value")));
        cols.add(m -> num(m.get("variacionAbsoluta")));
        cols.add(m -> num(m.get("variacionPorcentual")));
        if (tres) {
            headers.addAll(List.of("Periodo C", "Saldo C", "Var. Abs. B-C", "Var. % B-C"));
            cols.add(m -> m.get("period3Label"));
            cols.add(m -> num(m.get("period3Value")));
            cols.add(m -> num(m.get("variacionAbsoluta2")));
            cols.add(m -> num(m.get("variacionPorcentual2")));
        }

        String periodLabel = labelA + "-vs-" + labelB + (tres ? "-vs-" + labelC : "");
        ExportResult result;
        String fmt = format != null ? format.toLowerCase() : "csv";
        if ("pdf".equals(fmt)) {
            // HU-CG-13 E4: el PDF del comparativo incluye una grafica de barras
            // por clase comparando los periodos. CSV/XLSX siguen tabulares.
            List<ChartBar> bars = buildComparativoBars(list, tres);
            byte[] pdfBytes = buildPdf("Comparativo", periodLabel, ctx,
                    headers, cols, list, null, bars);
            result = new ExportResult(pdfBytes, "Comparativo-" + periodLabel + ".pdf", PDF_MIME);
        } else {
            // HU-CG-13 E4 (QA reeval Q3): la grafica comparativa antes SOLO salia en el
            // PDF. QA confirmo que XLSX y CSV no la incluian. Ahora:
            //  - XLSX: tabla + grafica de barras NATIVA de Excel (XSSFChart) en una hoja
            //    "Grafica", una serie por periodo, editable por el contador.
            //  - CSV: tabla + seccion con los datos por clase/periodo que alimentan la
            //    grafica (un CSV no admite imagenes, pero si los datos de la grafica).
            List<ChartBar> bars = buildComparativoBars(list, tres);
            if ("xlsx".equals(fmt)) {
                byte[] xlsx = buildComparativoXlsxWithChart(ctx, headers, cols, list, bars);
                result = new ExportResult(xlsx, "Comparativo-" + periodLabel + ".xlsx",
                        SimpleTableExporter.XLSX_MIME);
            } else {
                byte[] csv = buildComparativoCsvWithChartData(ctx, headers, cols, list, bars);
                result = new ExportResult(csv, "Comparativo-" + periodLabel + ".csv",
                        SimpleTableExporter.CSV_MIME);
            }
        }
        publishExportAudit("Comparativo", year1, month1, format, list.size());
        return result;
    }

    /**
     * HU-CG-13 E4 (QA reeval Q3): construye el XLSX del comparativo con la tabla en
     * la hoja "Comparativo" y la grafica de barras NATIVA de Excel en la hoja
     * "Grafica" (datos + {@link XSSFChart}). La grafica queda editable en Excel.
     */
    private byte[] buildComparativoXlsxWithChart(
            ReportHeaderBuilder.ReportContext ctx,
            List<String> headers,
            List<Function<java.util.Map<String, Object>, Object>> cols,
            List<java.util.Map<String, Object>> list,
            List<ChartBar> bars) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Comparativo");
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            int startRow = 0;
            if (ctx != null) {
                startRow = ReportHeaderBuilder.writeXlsxHeader(wb, sheet, ctx, headers.size());
            }
            Row hr = sheet.createRow(startRow);
            for (int i = 0; i < headers.size(); i++) {
                org.apache.poi.ss.usermodel.Cell c = hr.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, startRow + 1);
            int r = startRow + 1;
            for (java.util.Map<String, Object> row : list) {
                Row xr = sheet.createRow(r++);
                for (int i = 0; i < cols.size(); i++) {
                    Object v = cols.get(i).apply(row);
                    org.apache.poi.ss.usermodel.Cell c = xr.createCell(i);
                    if (v == null) {
                        c.setBlank();
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        c.setCellValue(v.toString());
                    }
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            if (bars != null && !bars.isEmpty()) {
                buildChartSheet(wb, bars);
            }
            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Error generando XLSX comparativo con grafica: " + e.getMessage(), e);
        }
    }

    /**
     * Crea la hoja "Grafica" con los datos (clase x periodo) y una grafica de
     * barras nativa de Excel ({@link XSSFChart}) que referencia esos datos.
     */
    private void buildChartSheet(XSSFWorkbook wb, List<ChartBar> bars) {
        XSSFSheet sheet = wb.createSheet("Grafica");
        int nSeries = bars.get(0).series.size();   // 2 (A vs B) o 3 (A vs B vs C)
        // Encabezado: Clase | <labelA> | <labelB> [| <labelC>]
        Row head = sheet.createRow(0);
        head.createCell(0).setCellValue("Clase");
        for (int s = 0; s < nSeries; s++) {
            head.createCell(s + 1).setCellValue(bars.get(0).series.get(s).label);
        }
        // Datos: una fila por clase
        for (int i = 0; i < bars.size(); i++) {
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(bars.get(i).groupLabel);
            for (int s = 0; s < nSeries; s++) {
                row.createCell(s + 1).setCellValue(bars.get(i).series.get(s).value);
            }
        }
        for (int i = 0; i <= nSeries; i++) sheet.autoSizeColumn(i);

        int lastRow = bars.size();   // datos en filas 1..lastRow (0-based)
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(
                0, 0, 0, 0, nSeries + 2, 0, nSeries + 14, 22);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Comparativo por clase contable");
        chart.setTitleOverlay(false);
        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis catAx = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis valAx = chart.createValueAxis(AxisPosition.LEFT);
        XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(1, lastRow, 0, 0));
        XDDFBarChartData bar = (XDDFBarChartData) chart.createData(ChartTypes.BAR, catAx, valAx);
        bar.setBarDirection(BarDirection.COL);
        for (int s = 0; s < nSeries; s++) {
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(
                    sheet, new CellRangeAddress(1, lastRow, s + 1, s + 1));
            XDDFChartData.Series series = bar.addSeries(cats, vals);
            series.setTitle(bars.get(0).series.get(s).label, null);
        }
        chart.plot(bar);
    }

    /**
     * HU-CG-13 E4 (QA reeval Q3): CSV del comparativo = tabla + seccion con los
     * datos que alimentan la grafica (por clase y periodo). Un CSV no admite
     * imagenes, pero si los datos de la grafica para analisis externo.
     */
    private byte[] buildComparativoCsvWithChartData(
            ReportHeaderBuilder.ReportContext ctx,
            List<String> headers,
            List<Function<java.util.Map<String, Object>, Object>> cols,
            List<java.util.Map<String, Object>> list,
            List<ChartBar> bars) {
        byte[] table = SimpleTableExporter.toCsv(headers, cols, list, ctx, null);
        if (bars == null || bars.isEmpty()) return table;
        DecimalFormat df = new DecimalFormat("#,##0.00",
                new DecimalFormatSymbols(new Locale("es", "CO")));
        int nSeries = bars.get(0).series.size();
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("GRAFICA COMPARATIVA POR CLASE (datos)").append('\n');
        sb.append("Clase");
        for (int s = 0; s < nSeries; s++) sb.append(';').append(bars.get(0).series.get(s).label);
        sb.append('\n');
        for (ChartBar b : bars) {
            sb.append(b.groupLabel);
            for (int s = 0; s < nSeries; s++) sb.append(';').append(df.format(b.series.get(s).value));
            sb.append('\n');
        }
        byte[] extra = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] out = new byte[table.length + extra.length];
        System.arraycopy(table, 0, out, 0, table.length);
        System.arraycopy(extra, 0, out, table.length, extra.length);
        return out;
    }

    /**
     * HU-CG-13 E4: construye las barras de la grafica del comparativo a partir
     * de las filas (una barra-grupo por clase, una serie por periodo). Se omiten
     * las clases sin valor en ningun periodo para no ensuciar la grafica.
     */
    private List<ChartBar> buildComparativoBars(
            List<java.util.Map<String, Object>> list, boolean tres) {
        List<ChartBar> bars = new ArrayList<>();
        for (java.util.Map<String, Object> row : list) {
            String name = str(row.get("className"));
            double a = num(row.get("period1Value"));
            double b = num(row.get("period2Value"));
            double c = tres ? num(row.get("period3Value")) : 0d;
            if (a == 0d && b == 0d && c == 0d) continue;
            List<ChartSeries> series = new ArrayList<>();
            series.add(new ChartSeries(str(row.get("period1Label")), a, new int[]{59, 130, 246}));
            series.add(new ChartSeries(str(row.get("period2Label")), b, new int[]{16, 185, 129}));
            if (tres) {
                series.add(new ChartSeries(str(row.get("period3Label")), c, new int[]{245, 158, 11}));
            }
            bars.add(new ChartBar(name, series));
        }
        return bars;
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
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

    /** Delegado sin grafica (Balance General, Estado Resultados, etc.). */
    private <T> byte[] buildPdf(String title, String period,
                                  ReportHeaderBuilder.ReportContext ctx,
                                  List<String> headers,
                                  List<Function<T, Object>> cols,
                                  List<T> rows,
                                  List<Object> totalsRow) {
        return buildPdf(title, period, ctx, headers, cols, rows, totalsRow, null);
    }

    private <T> byte[] buildPdf(String title, String period,
                                  ReportHeaderBuilder.ReportContext ctx,
                                  List<String> headers,
                                  List<Function<T, Object>> cols,
                                  List<T> rows,
                                  List<Object> totalsRow,
                                  List<ChartBar> chartBars) {
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

                // HU-CG-13 E4: grafica de barras por clase (solo comparativo)
                if (chartBars != null && !chartBars.isEmpty()) {
                    renderBarChart(doc, chartBars, titleFont, bodyFont, df);
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

    /**
     * HU-CG-13 E4: dibuja una grafica de barras horizontales nativa en iText
     * (sin librerias de charts). Una barra-grupo por clase; dentro, una barra
     * por periodo, con ancho proporcional al valor absoluto sobre el maximo
     * global. Cada periodo tiene su color (A azul, B verde, C ambar).
     */
    private void renderBarChart(Document doc, List<ChartBar> bars,
                                 PdfFont titleFont, PdfFont bodyFont, DecimalFormat df) {
        doc.add(new Paragraph("\nGrafica comparativa por clase")
                .setFont(titleFont).setFontSize(12)
                .setFontColor(new DeviceRgb(30, 58, 138)));

        double maxAbs = 0d;
        for (ChartBar b : bars) {
            for (ChartSeries s : b.series) {
                maxAbs = Math.max(maxAbs, Math.abs(s.value));
            }
        }
        if (maxAbs <= 0d) maxAbs = 1d;

        // Leyenda de colores por periodo
        if (!bars.isEmpty()) {
            Table legend = new Table(UnitValue.createPercentArray(
                    new float[]{1f, 99f})).useAllAvailableWidth();
            for (ChartSeries s : bars.get(0).series) {
                Cell swatch = new Cell()
                        .setBackgroundColor(new DeviceRgb(s.rgb[0], s.rgb[1], s.rgb[2]))
                        .setHeight(8f).setBorder(Border.NO_BORDER);
                Cell lbl = new Cell()
                        .add(new Paragraph("  " + s.label).setFont(bodyFont).setFontSize(7))
                        .setBorder(Border.NO_BORDER);
                legend.addCell(swatch);
                legend.addCell(lbl);
            }
            doc.add(legend);
        }

        for (ChartBar b : bars) {
            doc.add(new Paragraph(b.groupLabel)
                    .setFont(titleFont).setFontSize(8)
                    .setFontColor(new DeviceRgb(55, 65, 81)));
            Table chart = new Table(UnitValue.createPercentArray(
                    new float[]{16f, 60f, 24f})).useAllAvailableWidth();
            for (ChartSeries s : b.series) {
                chart.addCell(new Cell()
                        .add(new Paragraph(s.label).setFont(bodyFont).setFontSize(7))
                        .setBorder(Border.NO_BORDER));

                double pct = Math.abs(s.value) / maxAbs * 100d;
                Cell barCell = new Cell().setBorder(Border.NO_BORDER);
                if (pct >= 0.5d) {
                    float fill = (float) Math.min(100d, Math.max(1d, pct));
                    float rest = Math.max(0.01f, 100f - fill);
                    Table bar = new Table(UnitValue.createPercentArray(
                            new float[]{fill, rest})).useAllAvailableWidth();
                    bar.addCell(new Cell()
                            .setBackgroundColor(new DeviceRgb(s.rgb[0], s.rgb[1], s.rgb[2]))
                            .setHeight(9f).setBorder(Border.NO_BORDER));
                    bar.addCell(new Cell().setBorder(Border.NO_BORDER));
                    barCell.add(bar);
                }
                chart.addCell(barCell);

                chart.addCell(new Cell()
                        .add(new Paragraph(df.format(s.value))
                                .setFont(bodyFont).setFontSize(7)
                                .setTextAlignment(TextAlignment.RIGHT))
                        .setBorder(Border.NO_BORDER));
            }
            doc.add(chart);
        }
    }

    // Tipos auxiliares (clases con campos publicos para uso desde lambdas).

    /** HU-CG-13: una serie (periodo) dentro de una barra-grupo. */
    private static final class ChartSeries {
        public final String label;
        public final double value;
        public final int[] rgb;
        ChartSeries(String label, double value, int[] rgb) {
            this.label = label; this.value = value; this.rgb = rgb;
        }
    }

    /** HU-CG-13: una barra-grupo (clase) con sus series por periodo. */
    private static final class ChartBar {
        public final String groupLabel;
        public final List<ChartSeries> series;
        ChartBar(String groupLabel, List<ChartSeries> series) {
            this.groupLabel = groupLabel; this.series = series;
        }
    }

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

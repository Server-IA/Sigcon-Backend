package com.sigcon.backend.general.accounting.books.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.general.accounting.books.application.AuxiliarCuentaDTO;
import com.sigcon.backend.general.accounting.books.application.BalanceComprobacionDTO;
import com.sigcon.backend.general.accounting.books.application.LibroDiarioDTO;
import com.sigcon.backend.general.accounting.books.application.LibroMayorDTO;
import com.sigcon.backend.utils.export.ReportContextResolver;
import com.sigcon.backend.utils.export.ReportHeaderBuilder;
import com.sigcon.backend.utils.export.SimpleTableExporter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * QA Bloque BP (2026-05-18, HU-CG-14 E5 / HU-CG-15 E5): exportacion a CSV
 * y XLSX de los libros contables oficiales (Libro Diario, Libro Mayor,
 * Balance de Comprobacion, Auxiliares).
 *
 * <p>El PDF de los libros ya existia ({@link AccountingBookPdfService});
 * este service complementa con CSV (Excel ES con BOM UTF-8) y XLSX nativo
 * usando {@link SimpleTableExporter}. Cada export emite un evento EXPORT
 * en auditoria para trazabilidad.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingBookExportService {

    private final AccountingBookService accountingBookService;
    private final ReportContextResolver reportContextResolver;
    private final AuditPublisher auditPublisher;

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

    /** Exporta el Libro Diario en CSV o XLSX. */
    public ExportResult exportLibroDiario(Integer year, Integer month, String format) {
        List<LibroDiarioDTO> entries = accountingBookService.buildLibroDiario(year, month);
        // Aplanar: una fila por linea de asiento (para que sea util en Excel)
        List<DiarioRow> rows = new ArrayList<>();
        BigDecimal sumDeb = BigDecimal.ZERO;
        BigDecimal sumCre = BigDecimal.ZERO;
        for (LibroDiarioDTO d : entries) {
            if (d.getLines() == null) continue;
            for (LibroDiarioDTO.LibroDiarioLineDTO l : d.getLines()) {
                rows.add(new DiarioRow(d.getEntryNumber(), d.getVoucherCode(), d.getDate(),
                        l.getAccountCode(), l.getAccountName(),
                        l.getThirdPartyNit(), l.getDescription() != null
                                ? l.getDescription() : d.getDescription(),
                        l.getDebitAmount(), l.getCreditAmount()));
                if (l.getDebitAmount() != null) sumDeb = sumDeb.add(l.getDebitAmount());
                if (l.getCreditAmount() != null) sumCre = sumCre.add(l.getCreditAmount());
            }
        }

        String periodo = year + "-" + String.format("%02d", month);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Libro Diario - " + periodo)
                .addFilter("Periodo", periodo)
                .addTotal("Total Debitos", sumDeb)
                .addTotal("Total Creditos", sumCre)
                .build();

        List<String> headers = List.of("Asiento", "Codigo", "Fecha", "Cuenta PUC",
                "Nombre Cuenta", "NIT", "Descripcion", "Debito", "Credito");
        List<Function<DiarioRow, Object>> cols = new ArrayList<>();
        cols.add(r -> r.entryNumber);
        cols.add(r -> r.voucherCode);
        cols.add(r -> r.date != null ? r.date.toString() : "");
        cols.add(r -> r.accountCode);
        cols.add(r -> r.accountName);
        cols.add(r -> r.nit);
        cols.add(r -> r.description);
        cols.add(r -> r.debit != null ? r.debit.doubleValue() : 0d);
        cols.add(r -> r.credit != null ? r.credit.doubleValue() : 0d);

        List<Object> totalsRow = List.of("TOTAL",
                "(" + rows.size() + " lineas)", "", "", "", "", "",
                sumDeb.doubleValue(), sumCre.doubleValue());

        ExportResult res = encode("LibroDiario", periodo, format, headers, cols, rows,
                ctx, totalsRow);
        publishExportAudit("LibroDiario", year, month, format, rows.size());
        return res;
    }

    /** Exporta el Libro Mayor en CSV o XLSX. */
    public ExportResult exportLibroMayor(Integer year, Integer month, Long accountId,
                                          String format) {
        List<LibroMayorDTO> rows = accountingBookService.buildLibroMayor(year, month, accountId);
        BigDecimal sumDeb = rows.stream().map(r -> nz(r.getTotalDebit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCre = rows.stream().map(r -> nz(r.getTotalCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumSaldo = rows.stream().map(r -> nz(r.getBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);

        String periodo = year + "-" + String.format("%02d", month);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Libro Mayor - " + periodo)
                .addFilter("Periodo", periodo)
                .addFilter("Cuenta", accountId != null ? accountId.toString() : "Todas")
                .addTotal("Total Debitos", sumDeb)
                .addTotal("Total Creditos", sumCre)
                .build();

        List<String> headers = List.of("Codigo PUC", "Nombre Cuenta",
                "Total Debitos", "Total Creditos", "Saldo");
        List<Function<LibroMayorDTO, Object>> cols = new ArrayList<>();
        cols.add(r -> r.getPucCode());
        cols.add(r -> r.getAccountName());
        cols.add(r -> nz(r.getTotalDebit()).doubleValue());
        cols.add(r -> nz(r.getTotalCredit()).doubleValue());
        cols.add(r -> nz(r.getBalance()).doubleValue());

        List<Object> totalsRow = List.of("TOTAL",
                "(" + rows.size() + " cuentas)",
                sumDeb.doubleValue(), sumCre.doubleValue(), sumSaldo.doubleValue());

        ExportResult res = encode("LibroMayor", periodo, format, headers, cols, rows,
                ctx, totalsRow);
        publishExportAudit("LibroMayor", year, month, format, rows.size());
        return res;
    }

    /** Exporta el Balance de Comprobacion (HU-CG-16 E3) en CSV o XLSX. */
    public ExportResult exportBalanceComprobacion(Integer year, Integer month, String format) {
        List<BalanceComprobacionDTO> rows = accountingBookService.buildBalanceComprobacion(year, month);
        BigDecimal sumAntD = rows.stream().map(r -> nz(r.getSaldoAnteriorDebit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumAntC = rows.stream().map(r -> nz(r.getSaldoAnteriorCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumMovD = rows.stream().map(r -> nz(r.getMovimientoDebit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumMovC = rows.stream().map(r -> nz(r.getMovimientoCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumFinD = rows.stream().map(r -> nz(r.getSaldoFinalDebit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumFinC = rows.stream().map(r -> nz(r.getSaldoFinalCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);

        String periodo = year + "-" + String.format("%02d", month);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Balance de Comprobacion - " + periodo)
                .addFilter("Periodo", periodo)
                .addTotal("Total Movimientos Debito", sumMovD)
                .addTotal("Total Movimientos Credito", sumMovC)
                .build();

        List<String> headers = List.of("Codigo PUC", "Nombre Cuenta",
                "Saldo Ant. Debito", "Saldo Ant. Credito",
                "Mov. Debito", "Mov. Credito",
                "Saldo Final Debito", "Saldo Final Credito");
        List<Function<BalanceComprobacionDTO, Object>> cols = new ArrayList<>();
        cols.add(r -> r.getPucCode());
        cols.add(r -> r.getAccountName());
        cols.add(r -> nz(r.getSaldoAnteriorDebit()).doubleValue());
        cols.add(r -> nz(r.getSaldoAnteriorCredit()).doubleValue());
        cols.add(r -> nz(r.getMovimientoDebit()).doubleValue());
        cols.add(r -> nz(r.getMovimientoCredit()).doubleValue());
        cols.add(r -> nz(r.getSaldoFinalDebit()).doubleValue());
        cols.add(r -> nz(r.getSaldoFinalCredit()).doubleValue());

        List<Object> totalsRow = List.of("TOTAL",
                "(" + rows.size() + " cuentas)",
                sumAntD.doubleValue(), sumAntC.doubleValue(),
                sumMovD.doubleValue(), sumMovC.doubleValue(),
                sumFinD.doubleValue(), sumFinC.doubleValue());

        ExportResult res = encode("BalanceComprobacion", periodo, format, headers, cols,
                rows, ctx, totalsRow);
        publishExportAudit("BalanceComprobacion", year, month, format, rows.size());
        return res;
    }

    /** Exporta el Auxiliar de una cuenta del periodo. */
    public ExportResult exportAuxiliar(Integer year, Integer month, Long accountId, String format) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId es obligatorio para exportar Auxiliar.");
        }
        List<AuxiliarCuentaDTO> rows = accountingBookService.buildAuxiliaresCuentas(year, month, accountId);
        BigDecimal sumDeb = rows.stream().map(r -> nz(r.getDebit())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCre = rows.stream().map(r -> nz(r.getCredit())).reduce(BigDecimal.ZERO, BigDecimal::add);

        String periodo = year + "-" + String.format("%02d", month);
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Auxiliar Cuenta " + accountId + " - " + periodo)
                .addFilter("Periodo", periodo)
                .addFilter("Cuenta", accountId.toString())
                .addTotal("Total Debitos", sumDeb)
                .addTotal("Total Creditos", sumCre)
                .build();

        List<String> headers = List.of("Fecha", "Asiento", "Descripcion",
                "Debito", "Credito", "Saldo");
        List<Function<AuxiliarCuentaDTO, Object>> cols = new ArrayList<>();
        cols.add(r -> r.getDate() != null ? r.getDate().toString() : "");
        cols.add(r -> r.getEntryNumber());
        cols.add(r -> r.getDescription());
        cols.add(r -> nz(r.getDebit()).doubleValue());
        cols.add(r -> nz(r.getCredit()).doubleValue());
        cols.add(r -> nz(r.getRunningBalance()).doubleValue());

        List<Object> totalsRow = List.of("TOTAL", "(" + rows.size() + " mov.)", "",
                sumDeb.doubleValue(), sumCre.doubleValue(), "");

        ExportResult res = encode("Auxiliar_" + accountId, periodo, format, headers, cols,
                rows, ctx, totalsRow);
        publishExportAudit("Auxiliar", year, month, format, rows.size());
        return res;
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private void publishExportAudit(String type, Integer year, Integer month,
                                     String format, int rows) {
        try {
            auditPublisher.publish(AuditAction.EXPORT, AuditModule.CG, AuditSeverity.LOW,
                    "AccountingBook", null,
                    "Export " + type + " " + year + "-" + String.format("%02d", month)
                            + " formato=" + format + " filas=" + rows,
                    null, null, null);
        } catch (RuntimeException ignored) { /* audit no debe romper export */ }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
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
            default:
                throw new IllegalArgumentException("Formato no soportado: " + format
                        + ". Use csv o xlsx (para PDF use el endpoint /pdf existente).");
        }
    }

    // Tipo auxiliar para Libro Diario aplanado
    private static final class DiarioRow {
        public final Long entryNumber;
        public final String voucherCode;
        public final java.time.LocalDate date;
        public final String accountCode;
        public final String accountName;
        public final String nit;
        public final String description;
        public final BigDecimal debit;
        public final BigDecimal credit;
        DiarioRow(Long entryNumber, String voucherCode, java.time.LocalDate date,
                  String accountCode, String accountName, String nit,
                  String description, BigDecimal debit, BigDecimal credit) {
            this.entryNumber = entryNumber;
            this.voucherCode = voucherCode;
            this.date = date;
            this.accountCode = accountCode;
            this.accountName = accountName;
            this.nit = nit;
            this.description = description;
            this.debit = debit;
            this.credit = credit;
        }
    }
}

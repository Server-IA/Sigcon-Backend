package com.sigcon.backend.nomina.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sigcon.backend.parametrization.parameters.domain.service.SystemInfoService;
import com.sigcon.backend.nomina.domain.model.Employee;
import com.sigcon.backend.nomina.domain.model.PayrollLine;
import com.sigcon.backend.nomina.domain.model.PayrollReceipt;
import com.sigcon.backend.nomina.domain.repository.EmployeeRepository;
import com.sigcon.backend.nomina.domain.repository.PayrollLineRepository;
import com.sigcon.backend.nomina.domain.repository.PayrollReceiptRepository;
import com.sigcon.backend.lists_accounting.cost_centers.domain.repository.CostCenterRepository;
import com.sigcon.backend.utils.export.SimpleTableExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU-NOM-06: reportes del modulo de nomina.
 *
 * <ul>
 *   <li>E1: Comprobante individual por empleado en PDF (firma del empleador).</li>
 *   <li>E2: Reporte PILA exportable en CSV para operadores de seguridad social.</li>
 *   <li>E3: Resumen contable por periodo con desglose por centro de costo y
 *       referencia a los consecutivos de comprobantes CG generados.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollReportService {

    private final PayrollReceiptRepository receiptRepository;
    private final PayrollLineRepository lineRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemInfoService systemInfoService;
    private final AuditPublisher auditPublisher;
    private final CostCenterRepository costCenterRepository;

    private static final NumberFormat COP =
            NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    // ─────────────────────────────────────────────────────────────
    // HU-NOM-06 E1: comprobante individual en PDF
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public byte[] generateReceiptPdf(Long receiptId) {
        PayrollReceipt r = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado"));
        if (!"APPROVED".equals(r.getStatus()) && !"CLOSED".equals(r.getStatus())) {
            throw new IllegalStateException(
                    "Solo se pueden generar comprobantes de recibos APROBADOS o CERRADOS (estado actual: "
                    + r.getStatus() + ")");
        }
        Employee emp = employeeRepository.findById(r.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        List<PayrollLine> lines = lineRepository
                .findByReceiptIdAndDeletedAtIsNullOrderByLineOrder(r.getId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc);

            String companyName = Optional.ofNullable(systemInfoService.getCompanyName()).orElse("Empresa");
            String nit = Optional.ofNullable(systemInfoService.getCompanyNit()).orElse("");
            String dv = Optional.ofNullable(systemInfoService.getCompanyDv()).orElse("");

            doc.add(new Paragraph(companyName).setBold().setFontSize(14));
            doc.add(new Paragraph("NIT: " + nit + (dv.isBlank() ? "" : "-" + dv)).setFontSize(10));
            doc.add(new Paragraph("Comprobante de pago de nómina").setBold().setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));

            Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            header.addCell(cell("Empleado: " + emp.getFullName()));
            header.addCell(cell("Documento: " + emp.getDocumentType() + " " + emp.getDocumentNumber()));
            header.addCell(cell("Cargo: " + (emp.getPosition() != null ? emp.getPosition() : "-")));
            header.addCell(cell("Período: " + r.getPeriodYear() + "-"
                    + String.format("%02d", r.getPeriodMonth())));
            header.addCell(cell("Días trabajados: " + r.getDaysWorked()));
            // NOM-4 (2026-06-04): el comprobante mostraba el enum crudo en ingles
            // ("Tipo periodo: MONTHLY"). Se traduce al espaniol para el usuario final.
            header.addCell(cell("Tipo periodo: " + periodTypeLabel(r.getPeriodType())));
            doc.add(header);

            // Devengados
            doc.add(new Paragraph("Devengados").setBold().setFontSize(11));
            Table earnings = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth();
            earnings.addHeaderCell(headerCell("Concepto"));
            earnings.addHeaderCell(headerCell("Valor"));
            for (PayrollLine l : lines) {
                if (!"EARNING".equals(l.getLineType())) continue;
                earnings.addCell(cell(l.getConceptName()));
                earnings.addCell(cell(COP.format(l.getAmount())).setTextAlignment(TextAlignment.RIGHT));
            }
            earnings.addCell(cell("Total devengados").setBold());
            earnings.addCell(cell(COP.format(r.getTotalEarnings())).setBold()
                    .setTextAlignment(TextAlignment.RIGHT));
            doc.add(earnings);

            // Deducciones
            doc.add(new Paragraph("Deducciones").setBold().setFontSize(11));
            Table deductions = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth();
            deductions.addHeaderCell(headerCell("Concepto"));
            deductions.addHeaderCell(headerCell("Valor"));
            for (PayrollLine l : lines) {
                if (!"DEDUCTION".equals(l.getLineType())) continue;
                deductions.addCell(cell(l.getConceptName()));
                deductions.addCell(cell(COP.format(l.getAmount())).setTextAlignment(TextAlignment.RIGHT));
            }
            deductions.addCell(cell("Total deducciones").setBold());
            deductions.addCell(cell(COP.format(r.getTotalDeductions())).setBold()
                    .setTextAlignment(TextAlignment.RIGHT));
            doc.add(deductions);

            // Neto
            Table net = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth();
            net.addCell(cell("NETO A PAGAR").setBold().setFontSize(12));
            net.addCell(cell(COP.format(r.getNetPay())).setBold().setFontSize(12)
                    .setTextAlignment(TextAlignment.RIGHT));
            doc.add(net);

            // Firma del empleador
            doc.add(new Paragraph("\n\n").setFontSize(10));
            doc.add(new Paragraph("_______________________________").setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Firma del empleador - " + companyName)
                    .setTextAlignment(TextAlignment.CENTER).setFontSize(9));
            doc.add(new Paragraph("CST Art. 132 - Comprobante de pago de nómina obligatorio")
                    .setTextAlignment(TextAlignment.CENTER).setFontSize(8));
            doc.close();
            byte[] pdf = baos.toByteArray();
            auditPublisher.publish(AuditAction.EXPORT, AuditModule.NOM, AuditSeverity.LOW,
                    "PayrollReceipt", r.getId(),
                    "Comprobante PDF exportado: empleado=" + emp.getFullName()
                            + " periodo=" + r.getPeriodYear() + "-" + r.getPeriodMonth(),
                    null, null, null);
            return pdf;
        } catch (Exception e) {
            log.error("Error generando PDF de comprobante de nómina", e);
            throw new IllegalStateException("No se pudo generar el comprobante de pago: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HU-NOM-06 E2: reporte PILA CSV
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public byte[] generatePilaCsv(Integer year, Integer month) {
        List<PayrollReceipt> receipts = receiptRepository
                .findByPeriodYearAndPeriodMonthAndDeletedAtIsNull(year, month).stream()
                .filter(r -> "APPROVED".equals(r.getStatus()) || "CLOSED".equals(r.getStatus()))
                .collect(Collectors.toList());

        String companyNit = Optional.ofNullable(systemInfoService.getCompanyNit()).orElse("");

        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFFNIT_EMPRESA;DOC_EMPLEADO;NOMBRE;IBC;SALUD_EMP_4;PENSION_EMP_4;")
           .append("SALUD_EMPR_8_5;PENSION_EMPR_12;SENA_2;ICBF_3;CAJA_4;TOTAL_APORTES\n");

        for (PayrollReceipt r : receipts) {
            Optional<Employee> empOpt = employeeRepository.findById(r.getEmployeeId());
            if (empOpt.isEmpty()) continue;
            Employee emp = empOpt.get();
            BigDecimal ibc = r.getTotalEarnings();

            BigDecimal salud4 = pct(ibc, "4.00");
            BigDecimal pension4 = pct(ibc, "4.00");
            BigDecimal salud85 = pct(ibc, "8.50");
            BigDecimal pension12 = pct(ibc, "12.00");
            BigDecimal sena2 = pct(ibc, "2.00");
            BigDecimal icbf3 = pct(ibc, "3.00");
            BigDecimal caja4 = pct(ibc, "4.00");
            BigDecimal total = salud4.add(pension4).add(salud85).add(pension12)
                    .add(sena2).add(icbf3).add(caja4);

            csv.append(companyNit).append(';')
                    .append(emp.getDocumentNumber()).append(';')
                    .append(escape(emp.getFullName())).append(';')
                    .append(ibc.toPlainString()).append(';')
                    .append(salud4.toPlainString()).append(';')
                    .append(pension4.toPlainString()).append(';')
                    .append(salud85.toPlainString()).append(';')
                    .append(pension12.toPlainString()).append(';')
                    .append(sena2.toPlainString()).append(';')
                    .append(icbf3.toPlainString()).append(';')
                    .append(caja4.toPlainString()).append(';')
                    .append(total.toPlainString()).append('\n');
        }
        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        auditPublisher.publish(AuditAction.EXPORT, AuditModule.NOM, AuditSeverity.LOW,
                "PilaReport", null,
                "Reporte PILA exportado: periodo=" + year + "-" + month
                        + " recibos=" + receipts.size(),
                null, null, null);
        return bytes;
    }

    // NOM-5 (2026-06-04): PILA tambien en .txt (plano) y .xlsx (Excel) ademas
    // del CSV. Los 3 formatos comparten los mismos registros (buildPilaRecords).
    private static final List<String> PILA_HEADERS = List.of(
            "NIT_EMPRESA", "DOC_EMPLEADO", "NOMBRE", "IBC",
            "SALUD_EMP_4", "PENSION_EMP_4", "SALUD_EMPR_8_5", "PENSION_EMPR_12",
            "SENA_2", "ICBF_3", "CAJA_4", "TOTAL_APORTES");

    /** Columnas de monto (indice &ge; 3) -> celda numerica en XLSX. */
    private static final int PILA_FIRST_AMOUNT_COL = 3;

    /**
     * Construye los registros PILA del periodo. Solo recibos APROBADOS o
     * CERRADOS. Cada fila es un {@code String[]} con los montos como decimales
     * planos ({@code toPlainString}).
     */
    private List<String[]> buildPilaRecords(Integer year, Integer month) {
        List<PayrollReceipt> receipts = receiptRepository
                .findByPeriodYearAndPeriodMonthAndDeletedAtIsNull(year, month).stream()
                .filter(r -> "APPROVED".equals(r.getStatus()) || "CLOSED".equals(r.getStatus()))
                .collect(Collectors.toList());

        String companyNit = Optional.ofNullable(systemInfoService.getCompanyNit()).orElse("");
        List<String[]> rows = new ArrayList<>();

        for (PayrollReceipt r : receipts) {
            Optional<Employee> empOpt = employeeRepository.findById(r.getEmployeeId());
            if (empOpt.isEmpty()) continue;
            Employee emp = empOpt.get();
            BigDecimal ibc = r.getTotalEarnings();

            BigDecimal salud4 = pct(ibc, "4.00");
            BigDecimal pension4 = pct(ibc, "4.00");
            BigDecimal salud85 = pct(ibc, "8.50");
            BigDecimal pension12 = pct(ibc, "12.00");
            BigDecimal sena2 = pct(ibc, "2.00");
            BigDecimal icbf3 = pct(ibc, "3.00");
            BigDecimal caja4 = pct(ibc, "4.00");
            BigDecimal total = salud4.add(pension4).add(salud85).add(pension12)
                    .add(sena2).add(icbf3).add(caja4);

            rows.add(new String[]{
                    companyNit,
                    emp.getDocumentNumber(),
                    escape(emp.getFullName()),
                    ibc.toPlainString(),
                    salud4.toPlainString(), pension4.toPlainString(),
                    salud85.toPlainString(), pension12.toPlainString(),
                    sena2.toPlainString(), icbf3.toPlainString(),
                    caja4.toPlainString(), total.toPlainString()
            });
        }
        return rows;
    }

    private void auditPila(Integer year, Integer month, String format, int count) {
        auditPublisher.publish(AuditAction.EXPORT, AuditModule.NOM, AuditSeverity.LOW,
                "PilaReport", null,
                "Reporte PILA exportado (" + format + "): periodo=" + year + "-" + month
                        + " recibos=" + count,
                null, null, null);
    }

    /**
     * NOM-5: PILA en archivo plano de texto (.txt). Registros delimitados por
     * '|' (sin BOM), legible y parseable por operadores que consumen formato
     * plano.
     */
    @Transactional(readOnly = true)
    public byte[] generatePilaTxt(Integer year, Integer month) {
        List<String[]> rows = buildPilaRecords(year, month);
        StringBuilder txt = new StringBuilder();
        txt.append(String.join("|", PILA_HEADERS)).append('\n');
        for (String[] row : rows) {
            String[] safe = new String[row.length];
            for (int i = 0; i < row.length; i++) {
                safe[i] = row[i] == null ? ""
                        : row[i].replace('|', ' ').replace('\n', ' ').replace('\r', ' ');
            }
            txt.append(String.join("|", safe)).append('\n');
        }
        auditPila(year, month, "TXT", rows.size());
        return txt.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * NOM-5: PILA en Excel (.xlsx) via Apache POI. Las columnas de monto (IBC y
     * aportes) se escriben como celdas numericas reales (no texto) para que
     * Excel permita operar sin la advertencia "numero almacenado como texto".
     */
    @Transactional(readOnly = true)
    public byte[] generatePilaXlsx(Integer year, Integer month) {
        List<String[]> rows = buildPilaRecords(year, month);
        List<Function<String[], Object>> cols = new ArrayList<>();
        for (int i = 0; i < PILA_HEADERS.size(); i++) {
            final int idx = i;
            if (idx >= PILA_FIRST_AMOUNT_COL) {
                cols.add(row -> safeDouble(row[idx]));   // celda numerica
            } else {
                cols.add(row -> row[idx]);                // celda de texto
            }
        }
        byte[] xlsx = SimpleTableExporter.toXlsx(
                "PILA " + year + "-" + String.format("%02d", month),
                PILA_HEADERS, cols, rows);
        auditPila(year, month, "XLSX", rows.size());
        return xlsx;
    }

    /** Convierte un decimal plano a Double para celda numerica; si falla, deja el texto. */
    private static Object safeDouble(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return v; }
    }

    // ─────────────────────────────────────────────────────────────
    // HU-NOM-06 E3: resumen contable por periodo (JSON)
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Map<String, Object> periodAccountingSummary(Integer year, Integer month) {
        List<PayrollReceipt> receipts = receiptRepository
                .findByPeriodYearAndPeriodMonthAndDeletedAtIsNull(year, month);

        BigDecimal totalEarnings = receipts.stream()
                .map(PayrollReceipt::getTotalEarnings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeductions = receipts.stream()
                .map(PayrollReceipt::getTotalDeductions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEmployer = receipts.stream()
                .map(PayrollReceipt::getTotalEmployerContributions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet = receipts.stream()
                .map(PayrollReceipt::getNetPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Desglose por centro de costo (HU-NOM-06 E3 - 2026-04-28):
        // Las llaves del Map son nombres legibles ("CC-DEFAULT - Centro de Costo Default"),
        // no ids. Antes el frontend mostraba "CC #20" o "CC (sin asignar)" - confuso.
        Map<String, BigDecimal> earningsByCostCenter = new LinkedHashMap<>();
        Map<String, BigDecimal> netByCostCenter = new LinkedHashMap<>();
        for (PayrollReceipt r : receipts) {
            Long ccId = employeeRepository.findById(r.getEmployeeId())
                    .map(Employee::getCostCenterId).orElse(null);
            String ccLabel;
            if (ccId == null) {
                ccLabel = "(sin centro de costo)";
            } else {
                ccLabel = costCenterRepository.findById(ccId)
                        .map(cc -> {
                            String code = cc.getCode() != null ? cc.getCode() : ("#" + cc.getId());
                            String name = cc.getName() != null ? cc.getName() : "";
                            return name.isEmpty() ? code : (code + " - " + name);
                        })
                        .orElse("CC #" + ccId);
            }
            earningsByCostCenter.merge(ccLabel, r.getTotalEarnings(), BigDecimal::add);
            netByCostCenter.merge(ccLabel, r.getNetPay(), BigDecimal::add);
        }

        // Referencia a los JE consecutivos
        List<Long> journalEntryIds = receipts.stream()
                .map(PayrollReceipt::getJournalEntryId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("periodYear", year);
        resp.put("periodMonth", month);
        resp.put("totalReceipts", receipts.size());
        resp.put("totalEarnings", totalEarnings);
        resp.put("totalDeductions", totalDeductions);
        resp.put("totalEmployerContributions", totalEmployer);
        resp.put("totalNetPay", totalNet);
        resp.put("earningsByCostCenter", earningsByCostCenter);
        resp.put("netByCostCenter", netByCostCenter);
        resp.put("journalEntryIds", journalEntryIds);
        return resp;
    }

    // ======== Helpers ========

    private Cell cell(String text) {
        return new Cell().add(new Paragraph(text).setFontSize(9));
    }

    private Cell headerCell(String text) {
        return new Cell().add(new Paragraph(text).setBold().setFontSize(9));
    }

    private BigDecimal pct(BigDecimal base, String percentStr) {
        return base.multiply(new BigDecimal(percentStr))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace(";", ",");
    }

    /** NOM-4: traduce el enum de tipo de periodo al espaniol para el comprobante. */
    private String periodTypeLabel(String periodType) {
        if (periodType == null) return "-";
        switch (periodType) {
            case "MONTHLY":  return "Mensual";
            case "BIWEEKLY": return "Quincenal";
            case "WEEKLY":   return "Semanal";
            default:          return periodType;
        }
    }
}

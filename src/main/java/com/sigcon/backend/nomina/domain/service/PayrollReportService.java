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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
            header.addCell(cell("Tipo periodo: " + r.getPeriodType()));
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

        // Desglose por centro de costo
        Map<Long, BigDecimal> earningsByCostCenter = new LinkedHashMap<>();
        Map<Long, BigDecimal> netByCostCenter = new LinkedHashMap<>();
        for (PayrollReceipt r : receipts) {
            Long ccId = employeeRepository.findById(r.getEmployeeId())
                    .map(Employee::getCostCenterId).orElse(null);
            if (ccId == null) ccId = 0L; // "sin centro de costo"
            earningsByCostCenter.merge(ccId, r.getTotalEarnings(), BigDecimal::add);
            netByCostCenter.merge(ccId, r.getNetPay(), BigDecimal::add);
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
}

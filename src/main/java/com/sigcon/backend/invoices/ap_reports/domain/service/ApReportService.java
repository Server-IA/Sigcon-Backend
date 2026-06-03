package com.sigcon.backend.invoices.ap_reports.domain.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.itextpdf.layout.element.Paragraph;
import com.sigcon.backend.invoices.ap_notes.domain.model.ApCreditDebitNote;
import com.sigcon.backend.invoices.ap_notes.domain.repository.ApNoteRepository;
import com.sigcon.backend.invoices.ap_payments.domain.model.ApPayment;
import com.sigcon.backend.invoices.ap_payments.domain.repository.ApPaymentRepository;
import com.sigcon.backend.invoices.ap_reports.application.AgingReportDTO;
import com.sigcon.backend.invoices.ap_reports.application.SupplierStatementDTO;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.model.PurchaseOrder;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.PurchaseOrderRepository;
import com.sigcon.backend.reports.domain.service.ReportPdfService;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.export.ReportContextResolver;
import com.sigcon.backend.utils.export.ReportHeaderBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de reportes del modulo Cuentas por Pagar.
 * Genera reportes de antiguedad de saldos (aging) y estados de cuenta de proveedores.
 * Soporta salida en JSON y PDF (via iText7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApReportService {

    private final InvoiceRepository invoiceRepository;
    private final ApPaymentRepository paymentRepository;
    private final ApNoteRepository noteRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final ReportPdfService reportPdfService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    // QA Bloque BJ (2026-05-17): header estandar empresa+usuario+rol+filtros+totales
    private final ReportContextResolver reportContextResolver;

    private static final String RANGE_0_30 = "0-30 dias";
    private static final String RANGE_31_60 = "31-60 dias";
    private static final String RANGE_61_90 = "61-90 dias";
    private static final String RANGE_90_PLUS = "+90 dias";

    /**
     * Genera el reporte de antiguedad de saldos (aging report).
     * Consulta todas las facturas con saldo pendiente mayor a cero,
     * clasifica por dias de vencimiento y agrupa en rangos estandar.
     *
     * @return ResponseEntity con el reporte de antiguedad en formato JSON
     */
    public ResponseEntity<?> getAgingReport() {
        List<Invoices> pendingInvoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getBalanceDue() != null && inv.getBalanceDue() > 0)
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> bucketAmounts = new HashMap<>();
        Map<String, Integer> bucketCounts = new HashMap<>();
        bucketAmounts.put(RANGE_0_30, BigDecimal.ZERO);
        bucketAmounts.put(RANGE_31_60, BigDecimal.ZERO);
        bucketAmounts.put(RANGE_61_90, BigDecimal.ZERO);
        bucketAmounts.put(RANGE_90_PLUS, BigDecimal.ZERO);
        bucketCounts.put(RANGE_0_30, 0);
        bucketCounts.put(RANGE_31_60, 0);
        bucketCounts.put(RANGE_61_90, 0);
        bucketCounts.put(RANGE_90_PLUS, 0);

        BigDecimal totalPending = BigDecimal.ZERO;
        List<AgingReportDTO.AgingInvoiceDTO> invoiceDTOs = new ArrayList<>();

        for (Invoices inv : pendingInvoices) {
            LocalDate dueDate = inv.getInvoiceDate().plusDays(
                    inv.getInvoiceDueDay() != null ? inv.getInvoiceDueDay() : 30);
            long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
            String range = classifyRange(daysOverdue);
            BigDecimal balance = BigDecimal.valueOf(inv.getBalanceDue());

            bucketAmounts.merge(range, balance, BigDecimal::add);
            bucketCounts.merge(range, 1, Integer::sum);
            totalPending = totalPending.add(balance);

            String supplierName = "";
            try {
                if (inv.getThirdParty() != null) {
                    supplierName = inv.getThirdParty().getBusinessName() != null
                            ? inv.getThirdParty().getBusinessName()
                            : inv.getThirdParty().getNit();
                }
            } catch (Exception e) {
                supplierName = "N/A";
            }

            invoiceDTOs.add(AgingReportDTO.AgingInvoiceDTO.builder()
                    .invoiceId(inv.getId())
                    .invoiceNumber(inv.getResolutionInvoice())
                    .supplierName(supplierName)
                    .balanceDue(balance)
                    .daysOverdue(daysOverdue)
                    .range(range)
                    .build());
        }

        List<AgingReportDTO.AgingBucketDTO> buckets = List.of(
                AgingReportDTO.AgingBucketDTO.builder().range(RANGE_0_30)
                        .amount(bucketAmounts.get(RANGE_0_30)).count(bucketCounts.get(RANGE_0_30)).build(),
                AgingReportDTO.AgingBucketDTO.builder().range(RANGE_31_60)
                        .amount(bucketAmounts.get(RANGE_31_60)).count(bucketCounts.get(RANGE_31_60)).build(),
                AgingReportDTO.AgingBucketDTO.builder().range(RANGE_61_90)
                        .amount(bucketAmounts.get(RANGE_61_90)).count(bucketCounts.get(RANGE_61_90)).build(),
                AgingReportDTO.AgingBucketDTO.builder().range(RANGE_90_PLUS)
                        .amount(bucketAmounts.get(RANGE_90_PLUS)).count(bucketCounts.get(RANGE_90_PLUS)).build()
        );

        AgingReportDTO report = AgingReportDTO.builder()
                .totalPending(totalPending)
                .buckets(buckets)
                .invoices(invoiceDTOs)
                .build();

        log.info("Reporte de antiguedad generado: {} facturas pendientes, total: ${}", invoiceDTOs.size(), totalPending);
        return ResponseEntity.ok(report);
    }

    /**
     * Genera el reporte de antiguedad de saldos en formato PDF.
     * Utiliza ReportPdfService para construir el documento con encabezado corporativo.
     *
     * @return ResponseEntity con el PDF como byte array
     * @throws IOException si la generacion del PDF falla
     */
    public ResponseEntity<?> generateAgingPdf() throws IOException {
        // Obtener datos del reporte
        List<Invoices> pendingInvoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getBalanceDue() != null && inv.getBalanceDue() > 0)
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        List<Paragraph> body = new ArrayList<>();

        body.add(new Paragraph("Total facturas pendientes: " + pendingInvoices.size()));
        body.add(new Paragraph(" "));

        BigDecimal totalPending = BigDecimal.ZERO;
        for (Invoices inv : pendingInvoices) {
            LocalDate dueDate = inv.getInvoiceDate().plusDays(
                    inv.getInvoiceDueDay() != null ? inv.getInvoiceDueDay() : 30);
            long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
            String range = classifyRange(daysOverdue);
            BigDecimal balance = BigDecimal.valueOf(inv.getBalanceDue());
            totalPending = totalPending.add(balance);

            String supplierName = "";
            try {
                if (inv.getThirdParty() != null) {
                    supplierName = inv.getThirdParty().getBusinessName() != null
                            ? inv.getThirdParty().getBusinessName() : "N/A";
                }
            } catch (Exception e) {
                supplierName = "N/A";
            }

            body.add(new Paragraph(
                    inv.getResolutionInvoice() + " | " + supplierName
                            + " | Saldo: $" + balance + " | " + range + " (" + daysOverdue + " dias)"));
        }

        body.add(new Paragraph(" "));
        body.add(new Paragraph("TOTAL PENDIENTE: $" + totalPending));

        // QA Bloque BJ (2026-05-17): header estandar con empresa+usuario+rol+totales
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Reporte de Antiguedad de Saldos - Cuentas por Pagar")
                .addFilter("Fecha del reporte", today.toString())
                .addFilter("Facturas pendientes", String.valueOf(pendingInvoices.size()))
                .addTotal("Total Pendiente por Cobrar", totalPending)
                .build();
        byte[] pdfBytes = reportPdfService.generateEnhancedReport(
                "Reporte de Antiguedad de Saldos - Cuentas por Pagar", ctx, body);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=aging_report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * Genera el estado de cuenta de un proveedor especifico.
     * Incluye facturas, pagos y notas credito/debito con saldos acumulados.
     *
     * @param thirdPartyId identificador del tercero (proveedor)
     * @return ResponseEntity con el estado de cuenta del proveedor
     * @throws IllegalArgumentException si el proveedor no existe
     */
    public ResponseEntity<?> getSupplierStatement(Long thirdPartyId) {
        return ResponseEntity.ok(buildSupplierStatement(thirdPartyId));
    }

    /**
     * RF-11 (Notas Tecnicas CXP, 2026-06-02): construye el DTO del estado de
     * cuenta del proveedor. Reutilizado por el endpoint JSON y por el PDF.
     */
    private SupplierStatementDTO buildSupplierStatement(Long thirdPartyId) {
        ThirdParty supplier = thirdPartyRepository.findById(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no fue encontrado"));

        // Obtener facturas del proveedor
        List<Invoices> invoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getThirdParty() != null && inv.getThirdParty().getId().equals(thirdPartyId))
                .collect(Collectors.toList());

        List<SupplierStatementDTO.StatementLineDTO> lines = new ArrayList<>();
        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (Invoices inv : invoices) {
            BigDecimal invoiceAmount = BigDecimal.valueOf(inv.getTotalPayment() != null ? inv.getTotalPayment() : 0);
            totalInvoiced = totalInvoiced.add(invoiceAmount);

            lines.add(SupplierStatementDTO.StatementLineDTO.builder()
                    .type("FACTURA")
                    .documentNumber(inv.getResolutionInvoice())
                    .date(inv.getInvoiceDate())
                    .amount(invoiceAmount)
                    .balance(BigDecimal.valueOf(inv.getBalanceDue() != null ? inv.getBalanceDue() : 0))
                    .build());

            // Pagos de esta factura
            List<ApPayment> payments = paymentRepository.findByInvoiceIdAndDeletedAtIsNull(inv.getId());
            for (ApPayment payment : payments) {
                totalPaid = totalPaid.add(payment.getAmount());
                lines.add(SupplierStatementDTO.StatementLineDTO.builder()
                        .type("PAGO")
                        .documentNumber(payment.getPaymentReference() != null ? payment.getPaymentReference() : "P-" + payment.getId())
                        .date(payment.getPaymentDate())
                        .amount(payment.getAmount().negate())
                        .balance(null)
                        .build());
            }

            // Notas credito/debito de esta factura
            List<ApCreditDebitNote> notes = noteRepository.findByInvoiceIdAndDeletedAtIsNull(inv.getId());
            for (ApCreditDebitNote note : notes) {
                String type = "CREDIT".equals(note.getNoteType()) ? "NOTA_CREDITO" : "NOTA_DEBITO";
                BigDecimal noteAmount = "CREDIT".equals(note.getNoteType())
                        ? note.getAmount().negate() : note.getAmount();
                lines.add(SupplierStatementDTO.StatementLineDTO.builder()
                        .type(type)
                        .documentNumber(note.getNoteNumber())
                        .date(note.getCreatedAt().toLocalDate())
                        .amount(noteAmount)
                        .balance(null)
                        .build());
            }
        }

        String supplierName = supplier.getBusinessName() != null
                ? supplier.getBusinessName()
                : supplier.getNit();

        SupplierStatementDTO statement = SupplierStatementDTO.builder()
                .thirdPartyId(thirdPartyId)
                .supplierName(supplierName)
                .supplierNit(supplier.getNit())
                .totalInvoiced(totalInvoiced)
                .totalPaid(totalPaid)
                .totalBalance(totalInvoiced.subtract(totalPaid))
                .lines(lines)
                .build();

        log.info("Estado de cuenta generado para proveedor {} ({}): {} movimientos",
                supplierName, supplier.getNit(), lines.size());
        return statement;
    }

    /**
     * RF-11 (Notas Tecnicas CXP, 2026-06-02): estado de cuenta del proveedor en
     * PDF (iText7), con el mismo render estandar (encabezado empresa/usuario/
     * totales) que el reporte de aging. Reemplaza el window.print del frontend.
     *
     * @param thirdPartyId proveedor
     * @return ResponseEntity con el PDF
     * @throws IOException si la generacion falla
     */
    public ResponseEntity<?> generateSupplierStatementPdf(Long thirdPartyId) throws IOException {
        SupplierStatementDTO st = buildSupplierStatement(thirdPartyId);
        List<Paragraph> body = new ArrayList<>();
        body.add(new Paragraph("Proveedor: " + (st.getSupplierName() != null ? st.getSupplierName() : "-")
                + (st.getSupplierNit() != null ? " (NIT " + st.getSupplierNit() + ")" : "")));
        body.add(new Paragraph("Total Facturado: $" + bd(st.getTotalInvoiced())));
        body.add(new Paragraph("Total Pagado: $" + bd(st.getTotalPaid())));
        body.add(new Paragraph("Saldo Pendiente: $" + bd(st.getTotalBalance())));
        body.add(new Paragraph(" "));
        body.add(new Paragraph("Movimientos:"));
        if (st.getLines() == null || st.getLines().isEmpty()) {
            body.add(new Paragraph("Sin movimientos."));
        } else {
            for (SupplierStatementDTO.StatementLineDTO l : st.getLines()) {
                body.add(new Paragraph(
                        (l.getType() != null ? l.getType() : "-") + " | "
                        + (l.getDocumentNumber() != null ? l.getDocumentNumber() : "-") + " | "
                        + (l.getDate() != null ? l.getDate().toString() : "-")
                        + " | Monto: $" + bd(l.getAmount())
                        + (l.getBalance() != null ? " | Saldo: $" + l.getBalance() : "")));
            }
        }
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Estado de Cuenta Proveedor - Cuentas por Pagar")
                .addFilter("Proveedor", st.getSupplierName() != null ? st.getSupplierName() : "-")
                .addTotal("Saldo Pendiente", bd(st.getTotalBalance()))
                .build();
        byte[] pdfBytes = reportPdfService.generateEnhancedReport(
                "Estado de Cuenta Proveedor - Cuentas por Pagar", ctx, body);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estado_cuenta_proveedor.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /** RF-11: helper null-safe para BigDecimal. */
    private static BigDecimal bd(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ========================= Helpers privados =========================

    /**
     * Clasifica los dias de vencimiento en rangos estandar de antiguedad.
     *
     * @param daysOverdue dias de vencimiento
     * @return etiqueta del rango
     */
    private String classifyRange(long daysOverdue) {
        if (daysOverdue <= 30) return RANGE_0_30;
        if (daysOverdue <= 60) return RANGE_31_60;
        if (daysOverdue <= 90) return RANGE_61_90;
        return RANGE_90_PLUS;
    }

    /**
     * HU-AP-21 (2026-04-28): Reporte de Ordenes de Compra con filtros.
     * Devuelve resumen agregado por estado + detalle filtrable.
     *
     * @param thirdPartyId opcional, filtra por proveedor
     * @param status opcional, filtra por estado (DRAFT/PENDING/APPROVED/REJECTED/RECEIVED/CANCELLED)
     * @param dateFrom opcional, fecha inicial
     * @param dateTo opcional, fecha final
     * @return reporte con summaryByStatus + orders[]
     */
    public ResponseEntity<?> getPurchaseOrdersReport(Long thirdPartyId, String status,
                                                     LocalDate dateFrom, LocalDate dateTo) {
        return ResponseEntity.ok(buildPurchaseOrdersReport(thirdPartyId, status, dateFrom, dateTo));
    }

    /** RF-11: construye el Map del reporte de OCs (reutilizado por JSON y PDF). */
    private Map<String, Object> buildPurchaseOrdersReport(Long thirdPartyId, String status,
                                                          LocalDate dateFrom, LocalDate dateTo) {
        List<PurchaseOrder> orders = purchaseOrderRepository.findAll().stream()
                .filter(o -> thirdPartyId == null
                        || (o.getThirdParty() != null && thirdPartyId.equals(o.getThirdParty().getId())))
                .filter(o -> status == null || status.isBlank() || status.equalsIgnoreCase(o.getStatus()))
                .filter(o -> dateFrom == null || o.getOrderDate() == null
                        || !o.getOrderDate().isBefore(dateFrom))
                .filter(o -> dateTo == null || o.getOrderDate() == null
                        || !o.getOrderDate().isAfter(dateTo))
                .collect(Collectors.toList());

        Map<String, BigDecimal> amountByStatus = new HashMap<>();
        Map<String, Integer> countByStatus = new HashMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PurchaseOrder o : orders) {
            String st = o.getStatus() != null ? o.getStatus() : "UNKNOWN";
            BigDecimal amount = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            amountByStatus.merge(st, amount, BigDecimal::add);
            countByStatus.merge(st, 1, Integer::sum);
            grandTotal = grandTotal.add(amount);

            String thirdPartyName = "";
            String thirdPartyNit = "";
            try {
                if (o.getThirdParty() != null) {
                    thirdPartyName = o.getThirdParty().getBusinessName() != null
                            ? o.getThirdParty().getBusinessName() : "";
                    thirdPartyNit = o.getThirdParty().getNit() != null
                            ? o.getThirdParty().getNit() : "";
                }
            } catch (Exception ignored) { }

            Map<String, Object> row = new HashMap<>();
            row.put("id", o.getId());
            row.put("orderNumber", o.getOrderNumber());
            row.put("orderDate", o.getOrderDate());
            row.put("deliveryDate", o.getDeliveryDate());
            row.put("status", st);
            row.put("totalAmount", amount);
            row.put("thirdPartyName", thirdPartyName);
            row.put("thirdPartyNit", thirdPartyNit);
            row.put("notes", o.getNotes());
            rows.add(row);
        }

        List<Map<String, Object>> summary = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : amountByStatus.entrySet()) {
            Map<String, Object> bucket = new HashMap<>();
            bucket.put("status", e.getKey());
            bucket.put("count", countByStatus.getOrDefault(e.getKey(), 0));
            bucket.put("amount", e.getValue());
            summary.add(bucket);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("summaryByStatus", summary);
        resp.put("totalAmount", grandTotal);
        resp.put("totalCount", orders.size());
        resp.put("orders", rows);
        resp.put("filters", Map.of(
                "thirdPartyId", thirdPartyId == null ? "" : thirdPartyId,
                "status", status == null ? "" : status,
                "dateFrom", dateFrom == null ? "" : dateFrom.toString(),
                "dateTo", dateTo == null ? "" : dateTo.toString()
        ));
        return resp;
    }

    /**
     * RF-11 (Notas Tecnicas CXP, 2026-06-02): reporte de Ordenes de Compra en PDF
     * (iText7). Reemplaza el window.print del frontend.
     *
     * @return ResponseEntity con el PDF
     * @throws IOException si la generacion falla
     */
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> generatePurchaseOrdersReportPdf(Long thirdPartyId, String status,
                                                             LocalDate dateFrom, LocalDate dateTo) throws IOException {
        Map<String, Object> data = buildPurchaseOrdersReport(thirdPartyId, status, dateFrom, dateTo);
        List<Map<String, Object>> summary =
                (List<Map<String, Object>>) data.getOrDefault("summaryByStatus", new ArrayList<>());
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) data.getOrDefault("orders", new ArrayList<>());
        BigDecimal grandTotal = (BigDecimal) data.getOrDefault("totalAmount", BigDecimal.ZERO);

        List<Paragraph> body = new ArrayList<>();
        body.add(new Paragraph("Resumen por estado:"));
        for (Map<String, Object> s : summary) {
            body.add(new Paragraph("  " + s.get("status") + ": " + s.get("count")
                    + " OC(s) | $" + s.get("amount")));
        }
        body.add(new Paragraph("TOTAL: " + data.get("totalCount") + " OC(s) | $" + grandTotal));
        body.add(new Paragraph(" "));
        body.add(new Paragraph("Detalle:"));
        if (rows.isEmpty()) {
            body.add(new Paragraph("No se encontraron registros con los criterios seleccionados."));
        } else {
            for (Map<String, Object> r : rows) {
                body.add(new Paragraph(
                        r.getOrDefault("orderNumber", "-") + " | "
                        + r.getOrDefault("orderDate", "-") + " | "
                        + r.getOrDefault("thirdPartyName", "-") + " | NIT "
                        + r.getOrDefault("thirdPartyNit", "-") + " | "
                        + r.getOrDefault("status", "-") + " | $"
                        + r.getOrDefault("totalAmount", "0")));
            }
        }
        ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Reporte de Ordenes de Compra - Cuentas por Pagar")
                .addFilter("Ordenes", String.valueOf(data.getOrDefault("totalCount", 0)))
                .addTotal("Monto Total", grandTotal)
                .build();
        byte[] pdfBytes = reportPdfService.generateEnhancedReport(
                "Reporte de Ordenes de Compra - Cuentas por Pagar", ctx, body);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte_ordenes_compra.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}

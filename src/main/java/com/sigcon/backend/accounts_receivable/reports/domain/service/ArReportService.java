package com.sigcon.backend.accounts_receivable.reports.domain.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sigcon.backend.accounts_receivable.advances.domain.model.ArAdvance;
import com.sigcon.backend.accounts_receivable.advances.domain.repository.ArAdvanceRepository;
import com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.model.ArCreditDebitNote;
import com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.repository.ArNoteRepository;
import com.sigcon.backend.accounts_receivable.payments.domain.model.ArPayment;
import com.sigcon.backend.accounts_receivable.payments.domain.repository.ArPaymentRepository;
import com.sigcon.backend.accounts_receivable.reports.application.ArAgingBucketDTO;
import com.sigcon.backend.accounts_receivable.reports.application.ArCustomerBalanceDTO;
import com.sigcon.backend.accounts_receivable.reports.application.ArCustomerStatementDTO;
import com.sigcon.backend.accounts_receivable.reports.application.ArCustomerSummaryDTO;
import com.sigcon.backend.accounts_receivable.reports.application.ArInvoiceReportRow;
import com.sigcon.backend.accounts_receivable.reports.application.ArPeriodSummaryDTO;
import com.sigcon.backend.accounts_receivable.reports.application.ArReportRequest;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceStatus;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.reports.domain.service.ReportPdfService;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AR-05, AR-10, AR-12: Servicio de reportes de Cuentas por Cobrar.
 *
 * <p>Genera reportes por cliente, estado, periodo, aging de cartera,
 * saldo pendiente y estado de cuenta. Ofrece salida estructurada JSON
 * y versiones PDF via {@link ReportPdfService}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DeviceRgb TABLE_HEADER_BG = new DeviceRgb(30, 58, 138);
    private static final DeviceRgb TABLE_ALT_ROW = new DeviceRgb(238, 242, 255);

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final ArPaymentRepository arPaymentRepository;
    private final ArNoteRepository arNoteRepository;
    private final ArAdvanceRepository arAdvanceRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final ReportPdfService reportPdfService;

    // ========================= AR-05: Reporte por cliente =========================

    /**
     * AR-05: Reporte agrupado por cliente con totales y facturas del rango.
     */
    public List<ArCustomerSummaryDTO> reportByCustomer(ArReportRequest request) {
        validateDates(request);
        List<SalesInvoice> invoices;
        if (request.getThirdPartyId() != null) {
            invoices = salesInvoiceRepository.findByThirdPartyAndDateRange(
                    request.getThirdPartyId(), request.getStartDate(), request.getEndDate());
        } else {
            invoices = salesInvoiceRepository.findByInvoiceDateBetween(
                    request.getStartDate(), request.getEndDate());
        }

        Map<Long, List<SalesInvoice>> grouped = invoices.stream()
                .filter(i -> i.getThirdParty() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getThirdParty().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<ArCustomerSummaryDTO> result = new ArrayList<>();
        for (Map.Entry<Long, List<SalesInvoice>> entry : grouped.entrySet()) {
            List<SalesInvoice> list = entry.getValue();
            ThirdParty tp = list.get(0).getThirdParty();
            BigDecimal totalInvoiced = list.stream()
                    .map(i -> nz(i.getTotalAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPending = list.stream()
                    .map(i -> nz(i.getBalanceDue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<ArInvoiceReportRow> rows = list.stream()
                    .map(this::toRow)
                    .collect(Collectors.toList());

            result.add(ArCustomerSummaryDTO.builder()
                    .thirdPartyId(tp.getId())
                    .thirdPartyNit(tp.getNit())
                    .thirdPartyName(tp.getBusinessName())
                    .invoiceCount(list.size())
                    .totalInvoiced(totalInvoiced)
                    .totalPending(totalPending)
                    .invoices(rows)
                    .build());
        }
        return result;
    }

    // ========================= AR-05: Reporte por estado =========================

    /**
     * AR-05: Reporte filtrado por estado y rango de fechas.
     */
    /**
     * HU-AR-05 E2: lista facturas con saldo pendiente real (balanceDue > 0)
     * sin necesidad de elegir un status especifico. Excluye PAID/VOIDED/SETTLED
     * y respeta filtros opcionales por cliente y rango de fechas.
     */
    public List<ArInvoiceReportRow> reportOnlyPending(ArReportRequest request) {
        // HU-AR-05 E2: rango de fechas OPCIONAL (a diferencia de los otros reportes).
        // El usuario puede consultar todas sus facturas pendientes sin filtros.
        if (request != null && request.getStartDate() != null && request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        java.time.LocalDate from = request != null && request.getStartDate() != null
                ? request.getStartDate() : java.time.LocalDate.of(1900, 1, 1);
        java.time.LocalDate to = request != null && request.getEndDate() != null
                ? request.getEndDate() : java.time.LocalDate.of(2999, 12, 31);
        List<SalesInvoice> invoices = salesInvoiceRepository
                .findByInvoiceDateBetween(from, to);
        return invoices.stream()
                .filter(i -> i.getStatus() != SalesInvoiceStatus.PAID
                          && i.getStatus() != SalesInvoiceStatus.VOIDED
                          && i.getStatus() != SalesInvoiceStatus.SETTLED
                          && i.getStatus() != SalesInvoiceStatus.DRAFT
                          && nz(i.getBalanceDue()).compareTo(BigDecimal.ZERO) > 0)
                .filter(i -> request.getThirdPartyId() == null
                          || (i.getThirdParty() != null
                              && request.getThirdPartyId().equals(i.getThirdParty().getId())))
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    /**
     * HU-AR-12 E3: filas para estado de cuenta del cliente (todas las
     * facturas no anuladas/draft del cliente, en orden por fecha).
     */
    public List<ArInvoiceReportRow> getCustomerStatementRows(Long thirdPartyId) {
        return salesInvoiceRepository.findOpenInvoicesByThirdParty(thirdPartyId).stream()
                .filter(i -> i.getStatus() != SalesInvoiceStatus.DRAFT
                          && i.getStatus() != SalesInvoiceStatus.VOIDED)
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    public List<ArInvoiceReportRow> reportByStatus(ArReportRequest request) {
        validateDates(request);
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new IllegalArgumentException("Debe especificar un estado.");
        }
        SalesInvoiceStatus status;
        try {
            status = SalesInvoiceStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de factura no valido: " + request.getStatus());
        }
        return salesInvoiceRepository
                .findByStatusAndDateRange(status, request.getStartDate(), request.getEndDate())
                .stream().map(this::toRow).collect(Collectors.toList());
    }

    // ========================= AR-05: Reporte por periodo =========================

    /**
     * AR-05: Totales facturado, cobrado y pendiente en un periodo.
     */
    public ArPeriodSummaryDTO reportByPeriod(ArReportRequest request) {
        validateDates(request);
        List<SalesInvoice> invoices = salesInvoiceRepository
                .findByInvoiceDateBetween(request.getStartDate(), request.getEndDate());

        BigDecimal totalInvoiced = invoices.stream()
                .filter(i -> i.getStatus() != SalesInvoiceStatus.VOIDED)
                .map(i -> nz(i.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPending = invoices.stream()
                .filter(i -> i.getStatus() != SalesInvoiceStatus.VOIDED)
                .map(i -> nz(i.getBalanceDue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollected = totalInvoiced.subtract(totalPending);

        return ArPeriodSummaryDTO.builder()
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalInvoiced(totalInvoiced)
                .totalCollected(totalCollected)
                .totalPending(totalPending)
                .invoiceCount(invoices.size())
                .build();
    }

    // ========================= AR-10: Aging de cartera =========================

    /**
     * AR-10: Aging de cartera agrupado por buckets de dias de mora.
     */
    public List<ArAgingBucketDTO> aging() {
        LocalDate today = LocalDate.now();
        List<SalesInvoice> overdue = salesInvoiceRepository.findOverdueInvoices(today);

        Map<String, List<ArInvoiceReportRow>> buckets = new LinkedHashMap<>();
        buckets.put("0-30", new ArrayList<>());
        buckets.put("31-60", new ArrayList<>());
        buckets.put("61-90", new ArrayList<>());
        buckets.put("+90", new ArrayList<>());

        for (SalesInvoice inv : overdue) {
            ArInvoiceReportRow row = toRow(inv);
            long days = row.getDaysOverdue() != null ? row.getDaysOverdue() : 0L;
            if (days <= 30) buckets.get("0-30").add(row);
            else if (days <= 60) buckets.get("31-60").add(row);
            else if (days <= 90) buckets.get("61-90").add(row);
            else buckets.get("+90").add(row);
        }

        List<ArAgingBucketDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<ArInvoiceReportRow>> entry : buckets.entrySet()) {
            BigDecimal total = entry.getValue().stream()
                    .map(r -> nz(r.getBalanceDue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(ArAgingBucketDTO.builder()
                    .bucket(entry.getKey())
                    .totalBalance(total)
                    .invoiceCount(entry.getValue().size())
                    .invoices(entry.getValue())
                    .build());
        }
        return result;
    }

    // ========================= AR-10: Vencidas y proximas =========================

    /**
     * AR-10: Lista las facturas vencidas con mas de {@code days} dias de mora.
     * Si days es null se listan todas las vencidas.
     */
    public List<ArInvoiceReportRow> listOverdue(Integer days) {
        LocalDate today = LocalDate.now();
        List<SalesInvoice> list = salesInvoiceRepository.findOverdueInvoices(today);
        List<ArInvoiceReportRow> rows = list.stream().map(this::toRow).collect(Collectors.toList());
        if (days != null && days > 0) {
            long min = days.longValue();
            rows = rows.stream()
                    .filter(r -> r.getDaysOverdue() != null && r.getDaysOverdue() >= min)
                    .collect(Collectors.toList());
        }
        rows.sort(Comparator.comparing(ArInvoiceReportRow::getDaysOverdue,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    /**
     * AR-10: Lista facturas proximas a vencer en los proximos {@code days} dias.
     */
    public List<ArInvoiceReportRow> listUpcoming(Integer days) {
        int d = (days != null && days > 0) ? days : 7;
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(d);
        return salesInvoiceRepository.findUpcomingInvoices(today, end)
                .stream().map(this::toRow)
                .sorted(Comparator.comparing(ArInvoiceReportRow::getDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    // ========================= AR-12: Saldo y estado de cuenta =========================

    /**
     * AR-12: Saldo pendiente total de un cliente.
     *
     * @param thirdPartyId identificador del cliente
     * @param year         año opcional para filtrar por mes de facturacion
     * @param month        mes opcional (1-12)
     */
    public ArCustomerBalanceDTO customerBalance(Long thirdPartyId, Integer year, Integer month) {
        ThirdParty tp = thirdPartyRepository.findById(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException("El cliente no fue encontrado"));

        List<SalesInvoice> open = salesInvoiceRepository.findOpenInvoicesByThirdParty(thirdPartyId);
        if (year != null && month != null) {
            open = open.stream()
                    .filter(i -> i.getInvoiceDate() != null
                            && i.getInvoiceDate().getYear() == year
                            && i.getInvoiceDate().getMonthValue() == month)
                    .collect(Collectors.toList());
        }
        BigDecimal total = open.stream()
                .map(i -> nz(i.getBalanceDue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ArCustomerBalanceDTO.builder()
                .thirdPartyId(tp.getId())
                .thirdPartyNit(tp.getNit())
                .thirdPartyName(tp.getBusinessName())
                .totalPending(total)
                .openInvoiceCount(open.size())
                .openInvoices(open.stream().map(this::toRow).collect(Collectors.toList()))
                .build();
    }

    /**
     * AR-12: Estado de cuenta completo del cliente en el rango de fechas.
     * Integra facturas, cobros, NC/ND y anticipos en un libro de movimientos.
     */
    public ArCustomerStatementDTO customerStatement(Long thirdPartyId,
                                                     LocalDate startDate,
                                                     LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Debe especificar el rango de fechas.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
        ThirdParty tp = thirdPartyRepository.findById(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException("El cliente no fue encontrado"));

        List<SalesInvoice> invoices = salesInvoiceRepository
                .findByThirdPartyAndDateRange(thirdPartyId, startDate, endDate)
                .stream()
                .filter(i -> i.getStatus() != SalesInvoiceStatus.VOIDED)
                .collect(Collectors.toList());

        // Cobros y notas asociadas a las facturas del rango
        List<ArPayment> payments = new ArrayList<>();
        List<ArCreditDebitNote> notes = new ArrayList<>();
        for (SalesInvoice inv : invoices) {
            payments.addAll(arPaymentRepository.findByInvoiceIdAndDeletedAtIsNull(inv.getId()));
            notes.addAll(arNoteRepository.findByInvoiceIdAndDeletedAtIsNull(inv.getId()));
        }

        // Anticipos del cliente (sin restringir al rango, se filtra por fecha)
        List<ArAdvance> advances = new ArrayList<>();
        advances.addAll(arAdvanceRepository.findByThirdPartyIdAndStatusAndDeletedAtIsNull(thirdPartyId, "PENDING"));
        advances.addAll(arAdvanceRepository.findByThirdPartyIdAndStatusAndDeletedAtIsNull(thirdPartyId, "PARTIALLY_APPLIED"));
        advances.addAll(arAdvanceRepository.findByThirdPartyIdAndStatusAndDeletedAtIsNull(thirdPartyId, "FULLY_APPLIED"));
        advances = advances.stream()
                .filter(a -> a.getAdvanceDate() != null
                        && !a.getAdvanceDate().isBefore(startDate)
                        && !a.getAdvanceDate().isAfter(endDate))
                .collect(Collectors.toList());

        // Construir movimientos ordenados cronologicamente
        List<ArCustomerStatementDTO.StatementLine> lines = new ArrayList<>();
        for (SalesInvoice inv : invoices) {
            lines.add(ArCustomerStatementDTO.StatementLine.builder()
                    .date(inv.getInvoiceDate())
                    .type("FV")
                    .reference(inv.getInvoiceNumber())
                    .debit(nz(inv.getTotalAmount()))
                    .credit(BigDecimal.ZERO)
                    .build());
        }
        for (ArPayment p : payments) {
            lines.add(ArCustomerStatementDTO.StatementLine.builder()
                    .date(p.getPaymentDate())
                    .type("COBRO")
                    .reference(p.getPaymentReference() != null ? p.getPaymentReference() : "Cobro #" + p.getId())
                    .debit(BigDecimal.ZERO)
                    .credit(nz(p.getAmount()))
                    .build());
        }
        for (ArCreditDebitNote n : notes) {
            boolean isCredit = "CREDIT".equalsIgnoreCase(n.getNoteType());
            lines.add(ArCustomerStatementDTO.StatementLine.builder()
                    .date(n.getCreatedAt() != null ? n.getCreatedAt().toLocalDate() : startDate)
                    .type(isCredit ? "NC" : "ND")
                    .reference(n.getNoteNumber())
                    .debit(isCredit ? BigDecimal.ZERO : nz(n.getAmount()))
                    .credit(isCredit ? nz(n.getAmount()) : BigDecimal.ZERO)
                    .build());
        }
        for (ArAdvance a : advances) {
            lines.add(ArCustomerStatementDTO.StatementLine.builder()
                    .date(a.getAdvanceDate())
                    .type("ANTICIPO")
                    .reference(a.getAdvanceReference() != null ? a.getAdvanceReference() : "Anticipo #" + a.getId())
                    .debit(BigDecimal.ZERO)
                    .credit(nz(a.getAmount()))
                    .build());
        }
        lines.sort(Comparator.comparing(
                ArCustomerStatementDTO.StatementLine::getDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // Calcular saldo corriente
        BigDecimal running = BigDecimal.ZERO;
        for (ArCustomerStatementDTO.StatementLine l : lines) {
            running = running.add(nz(l.getDebit())).subtract(nz(l.getCredit()));
            l.setRunningBalance(running);
        }

        BigDecimal totalInvoiced = invoices.stream()
                .map(i -> nz(i.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollected = payments.stream()
                .map(p -> nz(p.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCN = notes.stream()
                .filter(n -> "CREDIT".equalsIgnoreCase(n.getNoteType()))
                .map(n -> nz(n.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDN = notes.stream()
                .filter(n -> "DEBIT".equalsIgnoreCase(n.getNoteType()))
                .map(n -> nz(n.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAdv = advances.stream()
                .map(a -> nz(a.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        return ArCustomerStatementDTO.builder()
                .thirdPartyId(tp.getId())
                .thirdPartyNit(tp.getNit())
                .thirdPartyName(tp.getBusinessName())
                .startDate(startDate)
                .endDate(endDate)
                .totalInvoiced(totalInvoiced)
                .totalCollected(totalCollected)
                .totalCreditNotes(totalCN)
                .totalDebitNotes(totalDN)
                .totalAdvances(totalAdv)
                .balance(running)
                .movements(lines)
                .build();
    }

    // ========================= PDF =========================

    /**
     * AR-05: Genera un PDF del reporte indicado por tipo.
     *
     * @param type    tipo de reporte: by-customer, by-status, by-period, aging, overdue, upcoming
     * @param request parametros del reporte
     */
    public byte[] generatePdf(String type, ArReportRequest request) throws IOException {
        String title;
        List<Paragraph> body;
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        switch (type != null ? type.toLowerCase() : "") {
            case "by-customer":
                title = "Reporte CxC por Cliente";
                body = buildCustomerReportBody(reportByCustomer(request), bold, regular);
                break;
            case "by-status":
                title = "Reporte CxC por Estado";
                body = buildInvoiceTableBody(reportByStatus(request), bold, regular);
                break;
            case "by-period":
                title = "Resumen CxC por Periodo";
                body = buildPeriodSummaryBody(reportByPeriod(request), bold, regular);
                break;
            case "aging":
                title = "Aging de Cartera";
                body = buildAgingBody(aging(), bold, regular);
                break;
            case "overdue":
                title = "Facturas Vencidas";
                body = buildInvoiceTableBody(listOverdue(null), bold, regular);
                break;
            case "upcoming":
                title = "Facturas Proximas a Vencer";
                body = buildInvoiceTableBody(listUpcoming(7), bold, regular);
                break;
            case "only-pending":
                // HU-AR-05 E2: PDF de solo facturas con saldo pendiente
                title = "Facturas con Saldo Pendiente";
                body = buildInvoiceTableBody(reportOnlyPending(request), bold, regular);
                break;
            case "customer-statement":
                // HU-AR-12 E3: PDF de estado de cuenta del cliente
                if (request.getThirdPartyId() == null) {
                    throw new IllegalArgumentException("Debe especificar el cliente.");
                }
                title = "Estado de Cuenta - Cliente " + request.getThirdPartyId();
                body = buildInvoiceTableBody(getCustomerStatementRows(request.getThirdPartyId()),
                        bold, regular);
                break;
            default:
                throw new IllegalArgumentException("Tipo de reporte no valido: " + type);
        }
        return reportPdfService.generateReport(title, body);
    }

    // ========================= Helpers =========================

    private ArInvoiceReportRow toRow(SalesInvoice inv) {
        ThirdParty tp = inv.getThirdParty();
        long daysOverdue = 0;
        if (inv.getDueDate() != null && inv.getBalanceDue() != null
                && inv.getBalanceDue().compareTo(BigDecimal.ZERO) > 0) {
            long d = ChronoUnit.DAYS.between(inv.getDueDate(), LocalDate.now());
            daysOverdue = Math.max(0, d);
        }
        return ArInvoiceReportRow.builder()
                .invoiceId(inv.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .thirdPartyId(tp != null ? tp.getId() : null)
                .thirdPartyNit(tp != null ? tp.getNit() : null)
                .thirdPartyName(tp != null ? tp.getBusinessName() : null)
                .invoiceDate(inv.getInvoiceDate())
                .dueDate(inv.getDueDate())
                .status(inv.getStatus() != null ? inv.getStatus().name() : null)
                .totalAmount(inv.getTotalAmount())
                .balanceDue(inv.getBalanceDue())
                .daysOverdue(daysOverdue)
                .build();
    }

    private void validateDates(ArReportRequest r) {
        if (r == null || r.getStartDate() == null || r.getEndDate() == null) {
            throw new IllegalArgumentException("Debe especificar el rango de fechas.");
        }
        if (r.getEndDate().isBefore(r.getStartDate())) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String formatMoney(BigDecimal v) {
        if (v == null) return "$0.00";
        return "$" + String.format("%,.2f", v);
    }

    // ---------------- Construccion de PDFs ----------------

    private List<Paragraph> buildInvoiceTableBody(List<ArInvoiceReportRow> rows, PdfFont bold, PdfFont regular) {
        List<Paragraph> body = new ArrayList<>();
        body.add(new Paragraph("Total facturas: " + rows.size())
                .setFont(regular).setFontSize(10).setMarginBottom(10));

        String[] headers = {"Numero", "Cliente", "NIT", "Fecha", "Vence", "Total", "Saldo", "Dias", "Estado"};
        float[] widths = {11f, 20f, 10f, 9f, 9f, 11f, 11f, 6f, 13f};
        Table table = new Table(UnitValue.createPercentArray(widths))
                .setWidth(UnitValue.createPercentValue(100)).setFontSize(7);
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                    .setBackgroundColor(TABLE_HEADER_BG).setPadding(4)
                    .add(new Paragraph(h).setFont(bold).setFontSize(7)
                            .setFontColor(ColorConstants.WHITE)
                            .setTextAlignment(TextAlignment.CENTER)));
        }
        int i = 0;
        for (ArInvoiceReportRow r : rows) {
            DeviceRgb bg = (i % 2 == 0) ? null : TABLE_ALT_ROW;
            addCell(table, r.getInvoiceNumber(), regular, bg);
            addCell(table, r.getThirdPartyName() != null ? r.getThirdPartyName() : "", regular, bg);
            addCell(table, r.getThirdPartyNit() != null ? r.getThirdPartyNit() : "", regular, bg);
            addCell(table, r.getInvoiceDate() != null ? r.getInvoiceDate().format(DATE_FMT) : "", regular, bg);
            addCell(table, r.getDueDate() != null ? r.getDueDate().format(DATE_FMT) : "", regular, bg);
            addCell(table, formatMoney(r.getTotalAmount()), regular, bg);
            addCell(table, formatMoney(r.getBalanceDue()), regular, bg);
            addCell(table, r.getDaysOverdue() != null ? r.getDaysOverdue().toString() : "0", regular, bg);
            addCell(table, r.getStatus() != null ? r.getStatus() : "", regular, bg);
            i++;
        }
        body.add(new Paragraph().add(table));
        return body;
    }

    private List<Paragraph> buildCustomerReportBody(List<ArCustomerSummaryDTO> summaries, PdfFont bold, PdfFont regular) {
        List<Paragraph> body = new ArrayList<>();
        body.add(new Paragraph("Clientes incluidos: " + summaries.size())
                .setFont(regular).setFontSize(10).setMarginBottom(10));
        for (ArCustomerSummaryDTO s : summaries) {
            body.add(new Paragraph(s.getThirdPartyName() + " (NIT: "
                    + s.getThirdPartyNit() + ") - Facturas: " + s.getInvoiceCount()
                    + " | Facturado: " + formatMoney(s.getTotalInvoiced())
                    + " | Pendiente: " + formatMoney(s.getTotalPending()))
                    .setFont(bold).setFontSize(9).setMarginTop(8).setMarginBottom(4));
            body.addAll(buildInvoiceTableBody(s.getInvoices(), bold, regular));
        }
        return body;
    }

    private List<Paragraph> buildPeriodSummaryBody(ArPeriodSummaryDTO s, PdfFont bold, PdfFont regular) {
        List<Paragraph> body = new ArrayList<>();
        body.add(new Paragraph("Periodo: "
                + s.getStartDate().format(DATE_FMT) + " al " + s.getEndDate().format(DATE_FMT))
                .setFont(bold).setFontSize(11).setMarginBottom(8));
        body.add(new Paragraph("Cantidad de facturas: " + s.getInvoiceCount())
                .setFont(regular).setFontSize(10));
        body.add(new Paragraph("Total facturado: " + formatMoney(s.getTotalInvoiced()))
                .setFont(regular).setFontSize(10));
        body.add(new Paragraph("Total cobrado: " + formatMoney(s.getTotalCollected()))
                .setFont(regular).setFontSize(10));
        body.add(new Paragraph("Total pendiente: " + formatMoney(s.getTotalPending()))
                .setFont(bold).setFontSize(10));
        return body;
    }

    private List<Paragraph> buildAgingBody(List<ArAgingBucketDTO> buckets, PdfFont bold, PdfFont regular) {
        List<Paragraph> body = new ArrayList<>();
        for (ArAgingBucketDTO b : buckets) {
            body.add(new Paragraph("Bucket " + b.getBucket() + " dias - Facturas: "
                    + b.getInvoiceCount() + " | Saldo: " + formatMoney(b.getTotalBalance()))
                    .setFont(bold).setFontSize(10).setMarginTop(8));
            if (b.getInvoices() != null && !b.getInvoices().isEmpty()) {
                body.addAll(buildInvoiceTableBody(b.getInvoices(), bold, regular));
            }
        }
        return body;
    }

    private void addCell(Table table, String value, PdfFont font, DeviceRgb bg) {
        Cell cell = new Cell().setPadding(3)
                .add(new Paragraph(value != null ? value : "").setFont(font).setFontSize(7));
        if (bg != null) cell.setBackgroundColor(bg);
        table.addCell(cell);
    }
}

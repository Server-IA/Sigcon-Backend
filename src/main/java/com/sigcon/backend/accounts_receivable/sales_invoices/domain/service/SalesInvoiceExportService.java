package com.sigcon.backend.accounts_receivable.sales_invoices.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceStatus;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.utils.export.ReportContextResolver;
import com.sigcon.backend.utils.export.ReportHeaderBuilder;
import com.sigcon.backend.utils.export.SimpleTableExporter;

import lombok.RequiredArgsConstructor;

/**
 * QA Bloque BN (HU-AR-01B reporte / exportar listado, 2026-05-18): export del
 * listado de Facturas de Venta a CSV/XLSX (+ PDF lo gestiona otra ruta).
 *
 * <p>Antes el frontend usaba el plugin nativo de DataTables (PDFmake/Excel
 * HTML5) para exportar, lo que producia archivos sin header de empresa, sin
 * usuario, sin filtros aplicados y sin fila TOTAL. Este service produce el
 * formato exigido por el lider del proyecto (mismo formato que ApReportService
 * "Estado de cuenta proveedor") usando ReportContextResolver + SimpleTableExporter
 * con la sobrecarga totalsRow agregada en este bloque.
 *
 * <p>Las columnas del export son las mismas que se muestran en el listado UI:
 * Id, # Factura, Cliente, Fecha, Vence, Moneda, Subtotal, IVA, Retencion,
 * Total, Saldo, Estado, Origen.
 */
@Service
@RequiredArgsConstructor
public class SalesInvoiceExportService {

    private final SalesInvoiceRepository repository;
    private final ReportContextResolver reportContextResolver;

    private static final List<String> HEADERS = List.of(
            "Id", "# Factura", "Cliente", "Fecha", "Vence", "Moneda",
            "Subtotal", "IVA", "Retencion", "Total", "Saldo", "Estado", "Origen");

    /** Resultado de un export: bytes + nombre de archivo sugerido + mime. */
    public static class ExportResult {
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
     * Genera el export en el formato pedido. {@code format} acepta "csv" o
     * "xlsx" (case-insensitive). Para PDF usar el endpoint dedicado de reportes
     * (ArReportService) que ya genera PDF con el formato unificado.
     *
     * <p>Los filtros (status, fechas, thirdPartyId) son opcionales y se reflejan
     * en el header del archivo como "Filtros aplicados". Si todos son null se
     * exporta el listado completo del tenant actual.
     */
    public ExportResult exportListing(String format,
                                      String status,
                                      LocalDate dateFrom,
                                      LocalDate dateTo,
                                      Long thirdPartyId) {
        // Construir Specification con los filtros opcionales. El @Filter de
        // tenant ya garantiza aislamiento; aqui solo agregamos los filtros
        // funcionales que el contador eligio.
        Specification<SalesInvoice> spec = Specification.where(null);
        if (status != null && !status.isBlank()) {
            try {
                final SalesInvoiceStatus s = SalesInvoiceStatus.valueOf(status);
                spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), s));
            } catch (IllegalArgumentException ignore) {
                // status invalido -> no filtra (defensivo). El listado del UI
                // solo permite valores validos del dropdown.
            }
        }
        if (dateFrom != null) {
            final LocalDate from = dateFrom;
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("invoiceDate"), from));
        }
        if (dateTo != null) {
            final LocalDate to = dateTo;
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("invoiceDate"), to));
        }
        if (thirdPartyId != null) {
            final Long tpId = thirdPartyId;
            spec = spec.and((root, q, cb) -> cb.equal(root.get("thirdParty").get("id"), tpId));
        }
        List<SalesInvoice> rows = repository.findAll(spec);

        // Header estandar (empresa+usuario+filtros+totales)
        ReportHeaderBuilder.ReportContext.Builder ctxB = reportContextResolver
                .baseContext("Reporte de Facturas de Venta");
        if (status != null && !status.isBlank()) {
            ctxB.addFilter("Estado", SalesInvoiceStatus.labelOf(status));
        }
        if (dateFrom != null) ctxB.addFilter("Fecha desde", dateFrom.toString());
        if (dateTo != null) ctxB.addFilter("Fecha hasta", dateTo.toString());
        if (thirdPartyId != null) ctxB.addFilter("Cliente ID", thirdPartyId.toString());

        // Calcular totales agregados para "Resumen" del header y para fila TOTAL.
        BigDecimal sumSubtotal = BigDecimal.ZERO;
        BigDecimal sumTax = BigDecimal.ZERO;
        BigDecimal sumWith = BigDecimal.ZERO;
        BigDecimal sumTotal = BigDecimal.ZERO;
        BigDecimal sumBalance = BigDecimal.ZERO;
        for (SalesInvoice si : rows) {
            sumSubtotal = sumSubtotal.add(nz(si.getSubtotal()));
            sumTax = sumTax.add(nz(si.getTotalTax()));
            sumWith = sumWith.add(nz(si.getTotalWithholding()));
            sumTotal = sumTotal.add(nz(si.getTotalAmount()));
            sumBalance = sumBalance.add(nz(si.getBalanceDue()));
        }
        ctxB.addTotal("Cantidad de facturas", BigDecimal.valueOf(rows.size()));
        ctxB.addTotal("Total general", sumTotal);
        ctxB.addTotal("Saldo total pendiente", sumBalance);
        ReportHeaderBuilder.ReportContext ctx = ctxB.build();

        // Columnas
        List<Function<SalesInvoice, Object>> columns = new ArrayList<>();
        columns.add(SalesInvoice::getId);
        columns.add(SalesInvoice::getInvoiceNumber);
        columns.add(si -> si.getThirdParty() != null ? si.getThirdParty().getBusinessName() : null);
        columns.add(SalesInvoice::getInvoiceDate);
        columns.add(SalesInvoice::getDueDate);
        columns.add(si -> si.getCurrency() != null ? si.getCurrency().getIsoCode() : "COP");
        columns.add(si -> nz(si.getSubtotal()).doubleValue());
        columns.add(si -> nz(si.getTotalTax()).doubleValue());
        columns.add(si -> nz(si.getTotalWithholding()).doubleValue());
        columns.add(si -> nz(si.getTotalAmount()).doubleValue());
        columns.add(si -> nz(si.getBalanceDue()).doubleValue());
        columns.add(si -> si.getStatus() != null ? si.getStatus().toLabelEs() : "");
        columns.add(si -> si.getIntegrationSource() != null && si.getIntegrationSource().getSource() != null
                ? si.getIntegrationSource().getSource().name()
                : "MANUAL");

        // Fila TOTAL alineada con las 13 columnas
        List<Object> totalsRow = List.of(
                "TOTAL",                           // Id
                "(" + rows.size() + " facturas)", // # Factura
                "",                                // Cliente
                "",                                // Fecha
                "",                                // Vence
                "",                                // Moneda
                sumSubtotal.doubleValue(),         // Subtotal
                sumTax.doubleValue(),              // IVA
                sumWith.doubleValue(),             // Retencion
                sumTotal.doubleValue(),            // Total
                sumBalance.doubleValue(),          // Saldo
                "",                                // Estado
                ""                                 // Origen
        );

        String fmt = format != null ? format.toLowerCase() : "csv";
        switch (fmt) {
            case "csv": {
                byte[] data = SimpleTableExporter.toCsv(HEADERS, columns, rows, ctx, totalsRow);
                return new ExportResult(data, "facturas-venta.csv", SimpleTableExporter.CSV_MIME);
            }
            case "xlsx": {
                byte[] data = SimpleTableExporter.toXlsx("Facturas Venta", HEADERS, columns, rows, ctx, totalsRow);
                return new ExportResult(data, "facturas-venta.xlsx", SimpleTableExporter.XLSX_MIME);
            }
            default:
                throw new IllegalArgumentException("Formato no soportado: " + format
                        + ". Use csv o xlsx (para pdf usar /api/v1/ar/reports/by-status/pdf).");
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

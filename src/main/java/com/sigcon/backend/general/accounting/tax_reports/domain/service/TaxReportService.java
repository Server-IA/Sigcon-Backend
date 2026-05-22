package com.sigcon.backend.general.accounting.tax_reports.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.general.accounting.tax_reports.application.EclProvisionReportDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.EclProvisionReportDTO.EclBucketDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.EclProvisionReportDTO.EclCustomerDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.ExchangeDifferenceReportDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.ExchangeDifferenceReportDTO.DifferenceItemDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.IvaReportDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.TaxesSummaryDTO;
import com.sigcon.backend.general.accounting.tax_reports.application.TaxesSummaryDTO.MonthlyTaxSummaryDTO;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeRate;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.repository.ExchangeRateRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de reportes contables-tributarios (HU-CG-31 a HU-CG-34).
 * <p>Genera:
 * <ul>
 *   <li>HU-CG-31: Provision ECL de cartera (NIIF 9)</li>
 *   <li>HU-CG-32: Cuadre IVA bimestral (Formulario 300 DIAN)</li>
 *   <li>HU-CG-33: Diferencias en cambio al cierre (NIC 21)</li>
 *   <li>HU-CG-34: Resumen consolidado anual de impuestos y retenciones</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaxReportService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    /**
     * Audit publisher opcional - HU-CG-12 E3 / HU-CG-31..34 (registrar
     * generacion y exportacion de reportes tributarios).
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.audit.domain.service.AuditPublisher auditPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.utils.export.ReportContextResolver reportContextResolver;

    private void publishViewAudit(String reportType, String period, int rows) {
        if (auditPublisher == null) return;
        try {
            auditPublisher.publish(
                    com.sigcon.backend.audit.domain.model.enums.AuditAction.VIEW,
                    com.sigcon.backend.audit.domain.model.enums.AuditModule.CG,
                    com.sigcon.backend.audit.domain.model.enums.AuditSeverity.LOW,
                    "TaxReport", null,
                    "Generacion " + reportType + " " + period + " filas=" + rows,
                    null, null, null);
        } catch (RuntimeException ignored) { /* audit no debe romper */ }
    }

    private void publishExportAudit(String reportType, String period, String format, int rows) {
        if (auditPublisher == null) return;
        try {
            auditPublisher.publish(
                    com.sigcon.backend.audit.domain.model.enums.AuditAction.EXPORT,
                    com.sigcon.backend.audit.domain.model.enums.AuditModule.CG,
                    com.sigcon.backend.audit.domain.model.enums.AuditSeverity.LOW,
                    "TaxReport", null,
                    "Export " + reportType + " " + period + " formato=" + format
                            + " filas=" + rows, null, null, null);
        } catch (RuntimeException ignored) { /* audit no debe romper */ }
    }

    @PersistenceContext
    private EntityManager entityManager;

    private static final String[] MONTH_LABELS = {
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    // ================================================================
    // HU-CG-31: Provision ECL de cartera
    // ================================================================

    /**
     * Calcula la Provision por Perdida Crediticia Esperada (ECL) al 31-dic
     * del anio indicado aplicando tasas NIIF 9 por tramos de mora.
     *
     * @param year anio de cierre
     * @return reporte ECL con buckets y detalle por cliente
     */
    public EclProvisionReportDTO generateEclProvision(Integer year) {
        validateYear(year);
        LocalDate cutoff = LocalDate.of(year, 12, 31);

        // Facturas con saldo pendiente al cierre. Se usa findAll() y se filtra
        // en memoria porque ya se excluyen VOIDED/PAID/SETTLED por balance_due.
        List<SalesInvoice> invoices = salesInvoiceRepository.findAll().stream()
            .filter(s -> s.getBalanceDue() != null && s.getBalanceDue().compareTo(BigDecimal.ZERO) > 0)
            .filter(s -> s.getStatus() == null || !s.getStatus().name().equals("VOIDED"))
            .filter(s -> s.getInvoiceDate() != null && !s.getInvoiceDate().isAfter(cutoff))
            .toList();

        // Buckets: 0-30, 31-60, 61-90, 91-180, >180
        BigDecimal[] rates = {
            new BigDecimal("0.01"), new BigDecimal("0.05"), new BigDecimal("0.20"),
            new BigDecimal("0.50"), new BigDecimal("1.00")
        };
        String[] labels = {"0-30 dias", "31-60 dias", "61-90 dias", "91-180 dias", "Mas de 180 dias"};
        BigDecimal[] bucketBalances = {
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        };
        BigDecimal[] bucketEcl = {
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        };

        Map<Long, EclCustomerDTO> byCustomer = new LinkedHashMap<>();
        BigDecimal totalCartera = BigDecimal.ZERO;
        BigDecimal totalProvision = BigDecimal.ZERO;

        for (SalesInvoice inv : invoices) {
            LocalDate due = inv.getDueDate() != null ? inv.getDueDate() : inv.getInvoiceDate();
            long daysOverdue = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(due, cutoff));
            int bucket = resolveBucket(daysOverdue);
            BigDecimal balance = inv.getBalanceDue();
            BigDecimal ecl = balance.multiply(rates[bucket]).setScale(2, RoundingMode.HALF_UP);

            bucketBalances[bucket] = bucketBalances[bucket].add(balance);
            bucketEcl[bucket] = bucketEcl[bucket].add(ecl);
            totalCartera = totalCartera.add(balance);
            totalProvision = totalProvision.add(ecl);

            ThirdParty tp = inv.getThirdParty();
            if (tp != null) {
                EclCustomerDTO acc = byCustomer.get(tp.getId());
                if (acc == null) {
                    acc = EclCustomerDTO.builder()
                        .thirdPartyId(tp.getId())
                        .nit(tp.getNit())
                        .name(tp.getBusinessName())
                        .totalBalance(BigDecimal.ZERO)
                        .totalEcl(BigDecimal.ZERO)
                        .build();
                    byCustomer.put(tp.getId(), acc);
                }
                acc.setTotalBalance(acc.getTotalBalance().add(balance));
                acc.setTotalEcl(acc.getTotalEcl().add(ecl));
            }
        }

        List<EclBucketDTO> buckets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            buckets.add(EclBucketDTO.builder()
                .label(labels[i])
                .totalBalance(bucketBalances[i])
                .eclRate(rates[i])
                .eclAmount(bucketEcl[i])
                .build());
        }

        EclProvisionReportDTO result = EclProvisionReportDTO.builder()
            .year(year)
            .totalCartera(totalCartera)
            .totalProvision(totalProvision)
            .buckets(buckets)
            .details(new ArrayList<>(byCustomer.values()))
            .build();
        publishViewAudit("ECL", String.valueOf(year), result.getDetails() != null ? result.getDetails().size() : 0);
        return result;
    }

    /**
     * Resuelve el indice de bucket segun dias en mora (0-30, 31-60, 61-90, 91-180, >180).
     */
    private int resolveBucket(long days) {
        if (days <= 30) return 0;
        if (days <= 60) return 1;
        if (days <= 90) return 2;
        if (days <= 180) return 3;
        return 4;
    }

    // ================================================================
    // HU-CG-32: Cuadre IVA bimestral
    // ================================================================

    /**
     * Calcula el IVA a cargo y a favor para un bimestre del anio.
     * Bimestre 1 = ene-feb, 2 = mar-abr, 3 = may-jun, 4 = jul-ago,
     * 5 = sep-oct, 6 = nov-dic.
     *
     * @param year anio gravable
     * @param bimester numero de bimestre (1-6)
     * @return reporte de IVA con saldo y conteo de facturas
     */
    public IvaReportDTO generateIvaBimestral(Integer year, Integer bimester) {
        validateYear(year);
        if (bimester == null || bimester < 1 || bimester > 6) {
            throw new IllegalArgumentException("El bimestre debe estar entre 1 y 6");
        }
        int startMonth = (bimester - 1) * 2 + 1;
        int endMonth = startMonth + 1;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        LocalDate end = LocalDate.of(year, endMonth, 1)
            .withDayOfMonth(LocalDate.of(year, endMonth, 1).lengthOfMonth());

        // IVA generado (ventas): suma total_tax + conteo facturas FV del bimestre
        Object[] arRow = (Object[]) entityManager.createNativeQuery(
            "SELECT COALESCE(SUM(total_tax),0), COUNT(*) FROM sales_invoices "
          + "WHERE invoice_date BETWEEN :start AND :end "
          + "AND status <> 'VOIDED' AND deleted_at IS NULL")
            .setParameter("start", start)
            .setParameter("end", end)
            .getSingleResult();
        BigDecimal ivaGenerado = toBigDecimal(arRow[0]);
        Integer countFV = ((Number) arRow[1]).intValue();

        // IVA descontable (compras AP): suma total_tax + conteo facturas FC
        Object[] apRow = (Object[]) entityManager.createNativeQuery(
            "SELECT COALESCE(SUM(total_tax),0), COUNT(*) FROM invoices "
          + "WHERE invoice_date BETWEEN :start AND :end "
          + "AND invoice_status <> 'VOIDED' AND deleted_at IS NULL")
            .setParameter("start", start)
            .setParameter("end", end)
            .getSingleResult();
        BigDecimal ivaDescontable = toBigDecimal(apRow[0]);
        Integer countFC = ((Number) apRow[1]).intValue();

        BigDecimal saldo = ivaGenerado.subtract(ivaDescontable);
        String tipo = saldo.compareTo(BigDecimal.ZERO) >= 0 ? "A pagar" : "A favor";

        String label = MONTH_LABELS[startMonth - 1] + "-" + MONTH_LABELS[endMonth - 1];

        IvaReportDTO result = IvaReportDTO.builder()
            .year(year)
            .bimester(bimester)
            .bimesterLabel(label)
            .ivaGenerado(ivaGenerado)
            .ivaDescontable(ivaDescontable)
            .saldoIva(saldo)
            .saldoTipo(tipo)
            .countFacturasVenta(countFV)
            .countFacturasCompra(countFC)
            .build();
        publishViewAudit("IVA bimestral", year + "-B" + bimester, countFV + countFC);
        return result;
    }

    // ================================================================
    // HU-CG-33: Diferencias en cambio
    // ================================================================

    /**
     * Calcula las diferencias en cambio por revaluacion de partidas monetarias
     * en moneda extranjera al cierre del mes indicado.
     *
     * @param year anio del cierre
     * @param month mes del cierre (1-12)
     * @return reporte de diferencias por factura
     */
    public ExchangeDifferenceReportDTO generateExchangeDifferences(Integer year, Integer month) {
        validateYear(year);
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("El mes debe estar entre 1 y 12");
        }
        LocalDate cutoff = LocalDate.of(year, month, 1)
            .withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth());

        // Localizar el id de la moneda COP (si no existe, no hay filtrado por COP)
        Long copId = currencyTypeRepository.findAll().stream()
            .filter(c -> "COP".equalsIgnoreCase(c.getIsoCode()))
            .map(CurrencyType::getId)
            .findFirst()
            .orElse(null);

        List<DifferenceItemDTO> items = new ArrayList<>();
        BigDecimal totalGanancia = BigDecimal.ZERO;
        BigDecimal totalPerdida = BigDecimal.ZERO;

        // Facturas de venta con moneda distinta a COP y saldo > 0
        List<SalesInvoice> fvInvoices = salesInvoiceRepository.findAll().stream()
            .filter(s -> s.getCurrency() != null)
            .filter(s -> copId == null || !copId.equals(s.getCurrency().getId()))
            .filter(s -> s.getBalanceDue() != null && s.getBalanceDue().compareTo(BigDecimal.ZERO) > 0)
            .filter(s -> s.getInvoiceDate() != null && !s.getInvoiceDate().isAfter(cutoff))
            .toList();

        for (SalesInvoice inv : fvInvoices) {
            BigDecimal originalRate = inv.getExchangeRate() != null ? inv.getExchangeRate() : BigDecimal.ONE;
            BigDecimal currentRate = findCurrentRate(inv.getCurrency().getId(), cutoff, originalRate);
            // balance_due esta en moneda extranjera (subtotal esta en extranjera segun el modelo AR)
            BigDecimal amountForeign = inv.getBalanceDue();
            BigDecimal diff = amountForeign.multiply(currentRate.subtract(originalRate))
                .setScale(2, RoundingMode.HALF_UP);
            // En AR (cuenta por cobrar), tasa sube = ganancia (recibiremos mas COP)
            String type = diff.compareTo(BigDecimal.ZERO) >= 0 ? "GANANCIA" : "PERDIDA";
            if (diff.compareTo(BigDecimal.ZERO) >= 0) totalGanancia = totalGanancia.add(diff);
            else totalPerdida = totalPerdida.add(diff.abs());

            items.add(DifferenceItemDTO.builder()
                .invoiceId(inv.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .documentType("FV")
                .currency(inv.getCurrency().getIsoCode())
                .amountForeign(amountForeign)
                .originalRate(originalRate)
                .currentRate(currentRate)
                .differenceAmount(diff)
                .type(type)
                .build());
        }

        // Facturas de compra (AP) con moneda extranjera.
        // Nota: la entidad Invoices (AP) no tiene currency_id ni exchangeRate
        // en el modelo actual. Antes de ejecutar la query verificamos si las
        // columnas existen en la BD (information_schema) para evitar que un
        // error SQL marque la transaccion como rollback-only.
        boolean apHasCurrencyColumns = false;
        try {
            Number cnt = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns "
              + "WHERE table_name = 'invoices' AND column_name IN ('currency_id','exchange_rate')")
                .getSingleResult();
            apHasCurrencyColumns = cnt != null && cnt.intValue() >= 2;
        } catch (Exception ignored) { /* noop */ }

        if (apHasCurrencyColumns) try {
            @SuppressWarnings("unchecked")
            List<Object[]> apRows = entityManager.createNativeQuery(
                "SELECT i.id, i.resolution_invoice, c.id AS currency_id, c.iso_code, "
              + "       i.balance_due, COALESCE(i.exchange_rate, 1) AS ex_rate "
              + "FROM invoices i "
              + "JOIN cfg_currency_types c ON c.id = i.currency_id "
              + "WHERE i.balance_due > 0 AND i.deleted_at IS NULL "
              + "AND i.invoice_status <> 'VOIDED' "
              + "AND i.invoice_date <= :cutoff "
              + (copId != null ? "AND c.id <> :copId " : ""))
                .setParameter("cutoff", cutoff)
                .setParameter("copId", copId)
                .getResultList();

            for (Object[] r : apRows) {
                Long invoiceId = ((Number) r[0]).longValue();
                String invoiceNumber = r[1] != null ? r[1].toString() : "";
                Long curId = ((Number) r[2]).longValue();
                String iso = r[3] != null ? r[3].toString() : "";
                BigDecimal amountForeign = toBigDecimal(r[4]);
                BigDecimal originalRate = toBigDecimal(r[5]);
                if (originalRate.compareTo(BigDecimal.ZERO) == 0) originalRate = BigDecimal.ONE;

                BigDecimal currentRate = findCurrentRate(curId, cutoff, originalRate);
                BigDecimal diff = amountForeign.multiply(currentRate.subtract(originalRate))
                    .setScale(2, RoundingMode.HALF_UP);
                // En AP (cuenta por pagar), tasa sube = perdida (pagaremos mas COP)
                String type = diff.compareTo(BigDecimal.ZERO) <= 0 ? "GANANCIA" : "PERDIDA";
                BigDecimal signedDiff = diff.negate();
                if (signedDiff.compareTo(BigDecimal.ZERO) >= 0) totalGanancia = totalGanancia.add(signedDiff);
                else totalPerdida = totalPerdida.add(signedDiff.abs());

                items.add(DifferenceItemDTO.builder()
                    .invoiceId(invoiceId)
                    .invoiceNumber(invoiceNumber)
                    .documentType("FC")
                    .currency(iso)
                    .amountForeign(amountForeign)
                    .originalRate(originalRate)
                    .currentRate(currentRate)
                    .differenceAmount(signedDiff)
                    .type(type)
                    .build());
            }
        } catch (Exception e) {
            // Si invoices no tiene columnas currency_id/exchange_rate, se omite AP
            log.debug("Saltando AP en diferencias en cambio: {}", e.getMessage());
        }

        ExchangeDifferenceReportDTO result = ExchangeDifferenceReportDTO.builder()
            .year(year)
            .month(month)
            .totalGanancia(totalGanancia)
            .totalPerdida(totalPerdida)
            .diferenciaNeta(totalGanancia.subtract(totalPerdida))
            .items(items)
            .build();
        publishViewAudit("Diferencias en cambio",
                year + "-" + String.format("%02d", month),
                items.size());
        return result;
    }

    /**
     * Busca la tasa de cambio vigente al corte; si no existe, devuelve la tasa original.
     */
    private BigDecimal findCurrentRate(Long currencyId, LocalDate cutoff, BigDecimal fallback) {
        if (currencyId == null) return fallback;
        List<ExchangeRate> rates = exchangeRateRepository.findByDeletedAtIsNull();
        return rates.stream()
            .filter(r -> r.getCurrencyExchange() != null
                && currencyId.equals(r.getCurrencyExchange().getId()))
            .filter(r -> r.getStartDate() != null && !r.getStartDate().isAfter(cutoff))
            .filter(r -> r.getEndDate() == null || !r.getEndDate().isBefore(cutoff))
            .map(r -> r.getValue() != null
                ? BigDecimal.valueOf(r.getValue())
                : fallback)
            .findFirst()
            .orElse(fallback);
    }

    // ================================================================
    // HU-CG-34: Resumen consolidado de impuestos y retenciones
    // ================================================================

    /**
     * Genera el resumen anual de impuestos causados y retenciones practicadas
     * con desglose mensual.
     *
     * @param year anio gravable
     * @return DTO con totales anuales + 12 filas mensuales
     */
    public TaxesSummaryDTO generateTaxesSummary(Integer year) {
        validateYear(year);

        // Ventas por mes: iva generado + retenciones practicadas
        @SuppressWarnings("unchecked")
        List<Object[]> salesRows = entityManager.createNativeQuery(
            "SELECT EXTRACT(MONTH FROM invoice_date) AS m, "
          + "       COALESCE(SUM(total_tax),0) AS iva, "
          + "       COALESCE(SUM(total_withholding),0) AS ret "
          + "FROM sales_invoices "
          + "WHERE EXTRACT(YEAR FROM invoice_date) = :year "
          + "AND status <> 'VOIDED' AND deleted_at IS NULL "
          + "GROUP BY EXTRACT(MONTH FROM invoice_date)")
            .setParameter("year", year)
            .getResultList();

        Map<Integer, BigDecimal[]> salesByMonth = new LinkedHashMap<>();
        for (Object[] r : salesRows) {
            int m = ((Number) r[0]).intValue();
            salesByMonth.put(m, new BigDecimal[]{ toBigDecimal(r[1]), toBigDecimal(r[2]) });
        }

        // Compras por mes: iva descontable + retenciones practicadas (total_discount como proxy)
        @SuppressWarnings("unchecked")
        List<Object[]> buyRows = entityManager.createNativeQuery(
            "SELECT EXTRACT(MONTH FROM invoice_date) AS m, "
          + "       COALESCE(SUM(total_tax),0) AS iva, "
          + "       COALESCE(SUM(total_discount),0) AS ret "
          + "FROM invoices "
          + "WHERE EXTRACT(YEAR FROM invoice_date) = :year "
          + "AND invoice_status <> 'VOIDED' AND deleted_at IS NULL "
          + "GROUP BY EXTRACT(MONTH FROM invoice_date)")
            .setParameter("year", year)
            .getResultList();

        Map<Integer, BigDecimal[]> buysByMonth = new LinkedHashMap<>();
        for (Object[] r : buyRows) {
            int m = ((Number) r[0]).intValue();
            buysByMonth.put(m, new BigDecimal[]{ toBigDecimal(r[1]), toBigDecimal(r[2]) });
        }

        BigDecimal totalIvaGen = BigDecimal.ZERO;
        BigDecimal totalIvaDesc = BigDecimal.ZERO;
        BigDecimal totalRetPrac = BigDecimal.ZERO;
        BigDecimal totalRetSop = BigDecimal.ZERO;

        List<MonthlyTaxSummaryDTO> monthly = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            BigDecimal[] s = salesByMonth.getOrDefault(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] b = buysByMonth.getOrDefault(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            BigDecimal ivaGen = s[0];
            BigDecimal ivaDesc = b[0];
            // Retenciones practicadas por la empresa = las que retuvimos a proveedores (AP)
            //   + las que retuvimos a clientes en ventas (total_withholding de FV)
            BigDecimal retPrac = s[1].add(b[1]);
            // Retenciones soportadas (placeholder): por ahora 0
            BigDecimal retSop = BigDecimal.ZERO;

            totalIvaGen = totalIvaGen.add(ivaGen);
            totalIvaDesc = totalIvaDesc.add(ivaDesc);
            totalRetPrac = totalRetPrac.add(retPrac);
            totalRetSop = totalRetSop.add(retSop);

            monthly.add(MonthlyTaxSummaryDTO.builder()
                .month(m)
                .monthLabel(MONTH_LABELS[m - 1])
                .ivaGenerado(ivaGen)
                .ivaDescontable(ivaDesc)
                .saldoIva(ivaGen.subtract(ivaDesc))
                .retencionesPracticadas(retPrac)
                .retencionesSoportadas(retSop)
                .build());
        }

        TaxesSummaryDTO result = TaxesSummaryDTO.builder()
            .year(year)
            .totalIvaGenerado(totalIvaGen)
            .totalIvaDescontable(totalIvaDesc)
            .saldoIvaAnual(totalIvaGen.subtract(totalIvaDesc))
            .totalRetencionesPracticadas(totalRetPrac)
            .totalRetencionesSoportadas(totalRetSop)
            .monthlySummary(monthly)
            .build();
        publishViewAudit("Resumen impuestos", String.valueOf(year), 12);
        return result;
    }

    // ──────────────────────────────────────────────────────────
    // HU-CG-12 E2 (QA 2026-05-19): exportacion CSV/XLSX del Resumen anual
    // de impuestos y retenciones (insumo para Formulario 350 DIAN).
    // ──────────────────────────────────────────────────────────

    /**
     * Exporta el resumen anual de impuestos en formato CSV o XLSX. Devuelve
     * el contenido binario listo para que el controller lo entregue como
     * archivo descargable.
     *
     * @param year   anio gravable
     * @param format "csv" o "xlsx"
     */
    public byte[] exportTaxesSummary(Integer year, String format) {
        TaxesSummaryDTO data = generateTaxesSummary(year);

        com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext ctx = null;
        if (reportContextResolver != null) {
            ctx = reportContextResolver.baseContext("Resumen Impuestos " + year)
                    .addFilter("Anio gravable", String.valueOf(year))
                    .addTotal("Total IVA Generado", data.getTotalIvaGenerado())
                    .addTotal("Total IVA Descontable", data.getTotalIvaDescontable())
                    .addTotal("Saldo IVA Anual", data.getSaldoIvaAnual())
                    .addTotal("Total Retenciones Practicadas", data.getTotalRetencionesPracticadas())
                    .build();
        }

        java.util.List<String> headers = java.util.List.of("Mes",
                "IVA Generado", "IVA Descontable", "Saldo IVA",
                "Retenciones Practicadas", "Retenciones Soportadas");
        java.util.List<java.util.function.Function<TaxesSummaryDTO.MonthlyTaxSummaryDTO, Object>> cols
                = new ArrayList<>();
        cols.add(m -> m.getMonthLabel());
        cols.add(m -> nzD(m.getIvaGenerado()));
        cols.add(m -> nzD(m.getIvaDescontable()));
        cols.add(m -> nzD(m.getSaldoIva()));
        cols.add(m -> nzD(m.getRetencionesPracticadas()));
        cols.add(m -> nzD(m.getRetencionesSoportadas()));

        java.util.List<Object> totalsRow = java.util.List.of("TOTAL ANUAL",
                nzD(data.getTotalIvaGenerado()),
                nzD(data.getTotalIvaDescontable()),
                nzD(data.getSaldoIvaAnual()),
                nzD(data.getTotalRetencionesPracticadas()),
                nzD(data.getTotalRetencionesSoportadas()));

        String fmt = normalizeFmt(format);
        byte[] content = emit("ResumenImpuestos " + year, headers, cols,
                data.getMonthlySummary(), ctx, totalsRow, fmt);
        publishExportAudit("Resumen impuestos", String.valueOf(year), fmt, 12);
        return content;
    }

    // ──────────────────────────────────────────────────────────
    // QA Bloque BR (HU-CG-12 E2): exportacion completa (CSV/XLSX/PDF) para
    // TODO el modulo de reportes tributarios — no solo el Resumen anual, sino
    // tambien IVA bimestral, ECL cartera y Diferencias en cambio.
    // ──────────────────────────────────────────────────────────

    /** Normaliza el formato y valida que sea uno de los soportados. */
    private static String normalizeFmt(String format) {
        String fmt = format != null ? format.toLowerCase() : "csv";
        if (!"csv".equals(fmt) && !"xlsx".equals(fmt) && !"pdf".equals(fmt)) {
            throw new IllegalArgumentException("Formato no soportado: " + format
                    + ". Use csv, xlsx o pdf.");
        }
        return fmt;
    }

    /** Despacha al exporter correcto segun formato (CSV/XLSX/PDF). */
    private <T> byte[] emit(String title, java.util.List<String> headers,
                            java.util.List<java.util.function.Function<T, Object>> cols,
                            java.util.List<T> rows,
                            com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext ctx,
                            java.util.List<Object> totalsRow, String fmt) {
        if ("xlsx".equals(fmt)) {
            return com.sigcon.backend.utils.export.SimpleTableExporter
                    .toXlsx(title, headers, cols, rows, ctx, totalsRow);
        } else if ("pdf".equals(fmt)) {
            return com.sigcon.backend.utils.export.SimpleTableExporter
                    .toPdf(title, headers, cols, rows, ctx, totalsRow);
        }
        return com.sigcon.backend.utils.export.SimpleTableExporter
                .toCsv(headers, cols, rows, ctx, totalsRow);
    }

    /**
     * HU-CG-12 E2: dispatcher de exportacion por tipo de reporte tributario.
     *
     * @param type    taxes-summary | iva | ecl | exchange-differences
     * @param year    anio gravable
     * @param month   mes (solo exchange-differences)
     * @param bimester bimestre (solo iva)
     * @param format  csv | xlsx | pdf
     */
    public byte[] exportReport(String type, Integer year, Integer month, Integer bimester, String format) {
        String fmt = normalizeFmt(format);
        switch (type != null ? type : "") {
            case "taxes-summary":        return exportTaxesSummary(year, fmt);
            case "iva":                  return exportIva(year, bimester, fmt);
            case "ecl":                  return exportEcl(year, fmt);
            case "exchange-differences": return exportExchangeDifferences(year, month, fmt);
            default:
                throw new IllegalArgumentException("Tipo de reporte tributario no soportado: " + type
                        + ". Use taxes-summary, iva, ecl o exchange-differences.");
        }
    }

    /** Exporta el IVA bimestral (HU-CG-12 E2). Una fila resumen del bimestre. */
    public byte[] exportIva(Integer year, Integer bimester, String format) {
        String fmt = normalizeFmt(format);
        IvaReportDTO data = generateIvaBimestral(year, bimester);
        com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext ctx = null;
        if (reportContextResolver != null) {
            ctx = reportContextResolver.baseContext("IVA Bimestral " + year)
                    .addFilter("Anio", String.valueOf(year))
                    .addFilter("Bimestre", String.valueOf(bimester))
                    .addTotal("IVA Generado", data.getIvaGenerado())
                    .addTotal("IVA Descontable", data.getIvaDescontable())
                    .addTotal("Saldo IVA", data.getSaldoIva())
                    .build();
        }
        java.util.List<String> headers = java.util.List.of("Bimestre",
                "IVA Generado", "IVA Descontable", "Saldo IVA", "Tipo Saldo",
                "# Fact. Venta", "# Fact. Compra");
        java.util.List<java.util.function.Function<IvaReportDTO, Object>> cols = new ArrayList<>();
        cols.add(d -> d.getBimesterLabel());
        cols.add(d -> nzD(d.getIvaGenerado()));
        cols.add(d -> nzD(d.getIvaDescontable()));
        cols.add(d -> nzD(d.getSaldoIva()));
        cols.add(d -> d.getSaldoTipo());
        cols.add(d -> d.getCountFacturasVenta());
        cols.add(d -> d.getCountFacturasCompra());
        byte[] content = emit("IVA Bimestral " + year, headers, cols,
                java.util.List.of(data), ctx, null, fmt);
        publishExportAudit("IVA Bimestral", year + "-B" + bimester, fmt, 1);
        return content;
    }

    /** Exporta la provision ECL de cartera por bucket NIIF 9 (HU-CG-12 E2). */
    public byte[] exportEcl(Integer year, String format) {
        String fmt = normalizeFmt(format);
        EclProvisionReportDTO data = generateEclProvision(year);
        com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext ctx = null;
        if (reportContextResolver != null) {
            ctx = reportContextResolver.baseContext("ECL Cartera NIIF 9 " + year)
                    .addFilter("Anio", String.valueOf(year))
                    .addTotal("Total Cartera", data.getTotalCartera())
                    .addTotal("Total Provision", data.getTotalProvision())
                    .build();
        }
        java.util.List<String> headers = java.util.List.of("Bucket (mora)",
                "Saldo Cartera", "Tasa ECL %", "Provision ECL");
        java.util.List<java.util.function.Function<EclProvisionReportDTO.EclBucketDTO, Object>> cols = new ArrayList<>();
        cols.add(b -> b.getLabel());
        cols.add(b -> nzD(b.getTotalBalance()));
        cols.add(b -> nzD(b.getEclRate()));
        cols.add(b -> nzD(b.getEclAmount()));
        java.util.List<Object> totalsRow = java.util.List.of("TOTAL",
                nzD(data.getTotalCartera()), "", nzD(data.getTotalProvision()));
        byte[] content = emit("ECL Cartera NIIF 9 " + year, headers, cols,
                data.getBuckets() != null ? data.getBuckets() : new ArrayList<>(), ctx, totalsRow, fmt);
        publishExportAudit("ECL Cartera", String.valueOf(year), fmt,
                data.getBuckets() != null ? data.getBuckets().size() : 0);
        return content;
    }

    /** Exporta las diferencias en cambio del periodo por documento (HU-CG-12 E2). */
    public byte[] exportExchangeDifferences(Integer year, Integer month, String format) {
        String fmt = normalizeFmt(format);
        ExchangeDifferenceReportDTO data = generateExchangeDifferences(year, month);
        com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext ctx = null;
        if (reportContextResolver != null) {
            ctx = reportContextResolver.baseContext("Diferencias en Cambio " + year + "-" + month)
                    .addFilter("Anio", String.valueOf(year))
                    .addFilter("Mes", String.valueOf(month))
                    .addTotal("Total Ganancia", data.getTotalGanancia())
                    .addTotal("Total Perdida", data.getTotalPerdida())
                    .addTotal("Diferencia Neta", data.getDiferenciaNeta())
                    .build();
        }
        java.util.List<String> headers = java.util.List.of("Documento", "Tipo",
                "Moneda", "Monto ME", "TRM Original", "TRM Actual", "Diferencia", "Efecto");
        java.util.List<java.util.function.Function<ExchangeDifferenceReportDTO.DifferenceItemDTO, Object>> cols = new ArrayList<>();
        cols.add(i -> i.getInvoiceNumber());
        cols.add(i -> i.getDocumentType());
        cols.add(i -> i.getCurrency());
        cols.add(i -> nzD(i.getAmountForeign()));
        cols.add(i -> nzD(i.getOriginalRate()));
        cols.add(i -> nzD(i.getCurrentRate()));
        cols.add(i -> nzD(i.getDifferenceAmount()));
        cols.add(i -> i.getType());
        java.util.List<Object> totalsRow = java.util.List.of("TOTAL NETO", "", "", "", "", "",
                nzD(data.getDiferenciaNeta()), "");
        byte[] content = emit("Diferencias en Cambio " + year + "-" + month, headers, cols,
                data.getItems() != null ? data.getItems() : new ArrayList<>(), ctx, totalsRow, fmt);
        publishExportAudit("Diferencias en cambio", year + "-" + month, fmt,
                data.getItems() != null ? data.getItems().size() : 0);
        return content;
    }

    private static double nzD(BigDecimal v) {
        return v != null ? v.doubleValue() : 0d;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private void validateYear(Integer year) {
        if (year == null || year < 1900 || year > 3000) {
            throw new IllegalArgumentException("Anio invalido");
        }
    }

    /** Convierte Object numerico (Double, BigDecimal, Number) a BigDecimal. */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }
}

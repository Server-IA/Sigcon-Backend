package com.sigcon.backend.banks.trm.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.utils.export.SimpleTableExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * BNK-HU-076 E4-E7: cálculo de diferencia en cambio (NIC 21) al cierre de conciliación
 * de cuentas en moneda extranjera, asiento propuesto y reporte de moneda extranjera.
 *
 * <p>El matching opera en moneda original (HU-076 E3); la diferencia en cambio se calcula
 * únicamente al cierre comparando la TRM de la fecha de cierre contra la TRM aplicada a
 * cada movimiento conciliado.
 *
 * <p>PUC (HU-076 E6): favorable (Σ&gt;0) → DB cuenta_bancaria / CR 421020 (ingreso);
 * desfavorable (Σ&lt;0) → DB 530530 (gasto) / CR cuenta_bancaria.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiferenciaCambioService {

    private static final String PUC_INGRESO = "421020"; // Diferencia en cambio (ingreso)
    private static final String PUC_GASTO   = "530530"; // Diferencia en cambio (gasto)
    private static final BigDecimal TOL = new BigDecimal("0.01");

    private final BankAccountRepository bankAccountRepository;
    private final FinancialMovementRepository movementRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final TrmService trmService;
    private final JournalEntryService journalEntryService;
    private final AuditPublisher auditPublisher;
    private final UserUtil userUtil;

    // ===================== E5: calcular diferencia =====================

    /**
     * HU-076 E4 + E5: valida cuadre en moneda original y calcula la diferencia en cambio
     * de todos los movimientos conciliados de la cuenta a la fecha de cierre.
     */
    public Map<String, Object> calcular(Long bankAccountId, LocalDate fechaCierre) {
        BankAccount ba = loadForeignAccount(bankAccountId);
        String iso = ba.getCurrencyType().getIsoCode();
        BigDecimal trmCierre = trmService.trmParaFecha(iso, fechaCierre);
        if (trmCierre == null) {
            throw new IllegalArgumentException("No hay TRM cargada para " + iso
                    + " a la fecha de cierre " + fechaCierre + ". Cargue la TRM antes de calcular la diferencia en cambio.");
        }

        List<FinancialMovement> all = movementRepository.findAllByBankAccountIdOrdered(bankAccountId);
        // HU-076 E4: cuadre en moneda original primero — no debe haber movimientos pendientes.
        long pendientes = all.stream()
                .filter(m -> !"CONCILIADO".equalsIgnoreCase(String.valueOf(m.getEstadoConciliacion())))
                .count();
        if (pendientes > 0) {
            throw new IllegalStateException("BNK-CON-028: Debe cuadrar en moneda original antes de aplicar diferencia en cambio. "
                    + "Quedan " + pendientes + " movimiento(s) sin conciliar en la cuenta.");
        }

        List<FinancialMovement> conciliados = all.stream()
                .filter(m -> "CONCILIADO".equalsIgnoreCase(String.valueOf(m.getEstadoConciliacion())))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> detalle = new ArrayList<>();
        List<String> sinTrm = new ArrayList<>();
        for (FinancialMovement m : conciliados) {
            BigDecimal montoOriginal = m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO;
            BigDecimal trmApl = m.getTrmAplicada();
            if (trmApl == null) {
                // Fallback: TRM de la fecha del movimiento (si tampoco hay, se omite y se reporta).
                trmApl = trmService.trmParaFecha(iso, m.getMovementDate());
            }
            if (trmApl == null) { sinTrm.add("#" + m.getId()); continue; }
            BigDecimal diffUnit = trmCierre.subtract(trmApl);
            BigDecimal diffTotal = diffUnit.multiply(montoOriginal).setScale(2, RoundingMode.HALF_UP);
            total = total.add(diffTotal);

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("movimientoId", m.getId());
            d.put("fecha", m.getMovementDate());
            d.put("descripcion", m.getDescription());
            d.put("montoOriginal", montoOriginal);
            d.put("trmAplicada", trmApl);
            d.put("trmCierre", trmCierre);
            d.put("diferencia", diffTotal);
            detalle.add(d);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bankAccountId", bankAccountId);
        out.put("moneda", iso);
        out.put("fechaCierre", fechaCierre);
        out.put("trmCierre", trmCierre);
        out.put("movimientosEvaluados", conciliados.size());
        out.put("diferenciaTotal", total);
        out.put("sentido", total.compareTo(BigDecimal.ZERO) > 0 ? "FAVORABLE_INGRESO"
                : total.compareTo(BigDecimal.ZERO) < 0 ? "DESFAVORABLE_GASTO" : "SIN_DIFERENCIA");
        out.put("detalle", detalle);
        if (!sinTrm.isEmpty()) out.put("movimientosSinTrm", sinTrm);
        return out;
    }

    // ===================== E6: generar asiento =====================

    /**
     * HU-076 E6: genera el comprobante de diferencia en cambio en BORRADOR.
     */
    @Transactional
    public Map<String, Object> generarAsiento(Long bankAccountId, LocalDate fechaCierre) {
        Map<String, Object> calc = calcular(bankAccountId, fechaCierre);
        BigDecimal total = (BigDecimal) calc.get("diferenciaTotal");
        if (total == null || total.abs().compareTo(TOL) <= 0) {
            throw new IllegalStateException("No hay diferencia en cambio que registrar "
                    + "(la TRM de cierre coincide con las TRM aplicadas).");
        }
        BankAccount ba = loadForeignAccount(bankAccountId);
        String iso = ba.getCurrencyType().getIsoCode();
        Long bancoAccId = ba.getAccountingAccount() != null ? ba.getAccountingAccount().getId() : null;
        if (bancoAccId == null)
            throw new IllegalStateException("La cuenta bancaria no tiene cuenta contable configurada.");

        BigDecimal monto = total.abs().setScale(2, RoundingMode.HALF_UP);
        boolean favorable = total.compareTo(BigDecimal.ZERO) > 0;
        String desc = "Diferencia en cambio " + iso + " cuenta " + ba.getCode()
                + " al " + fechaCierre + " (NIC 21)";

        List<CreateJournalEntryLineRequest> lines = new ArrayList<>();
        if (favorable) {
            // DB cuenta_bancaria / CR 421020 (ingreso)
            lines.add(line(bancoAccId, monto, BigDecimal.ZERO, desc));
            lines.add(line(resolvePuc(PUC_INGRESO), BigDecimal.ZERO, monto, desc));
        } else {
            // DB 530530 (gasto) / CR cuenta_bancaria
            lines.add(line(resolvePuc(PUC_GASTO), monto, BigDecimal.ZERO, desc));
            lines.add(line(bancoAccId, BigDecimal.ZERO, monto, desc));
        }

        CreateJournalEntryRequest jeReq = CreateJournalEntryRequest.builder()
                .entryDate(fechaCierre)
                .description(desc)
                .sourceModule(JournalSourceModule.BNK)
                .sourceId(bankAccountId)
                .lines(lines)
                .build();
        JournalEntryDTO je = journalEntryService.createEntry(jeReq, currentUser());

        String newValues = "{\"sentido\":\"" + (favorable ? "INGRESO_421020" : "GASTO_530530")
                + "\",\"monto\":" + monto + ",\"moneda\":\"" + iso + "\",\"trmCierre\":" + calc.get("trmCierre") + "}";
        auditPublisher.publish(AuditAction.CREATE, AuditModule.BNK, AuditSeverity.MEDIUM,
                "JournalEntry", je.getId(),
                "Diferencia en cambio " + iso + " cuenta=" + bankAccountId + " monto=$" + monto,
                null, newValues, je.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("comprobanteId", je.getId());
        out.put("voucherCode", safeVoucher(je));
        out.put("estadoComprobante", "BORRADOR");
        out.put("moneda", iso);
        out.put("diferenciaTotal", total);
        out.put("montoAsiento", monto);
        out.put("cuentaIngreso", favorable ? PUC_INGRESO : null);
        out.put("cuentaGasto", favorable ? null : PUC_GASTO);
        return out;
    }

    // ===================== E7: reporte moneda extranjera =====================

    /** HU-076 E7: reporte de movimientos en moneda extranjera del período con comparativa de TRM. */
    public Map<String, Object> reporte(Long bankAccountId, LocalDate fechaCierre,
                                       LocalDate desde, LocalDate hasta) {
        BankAccount ba = loadForeignAccount(bankAccountId);
        String iso = ba.getCurrencyType().getIsoCode();
        BigDecimal trmCierre = fechaCierre != null ? trmService.trmParaFecha(iso, fechaCierre) : null;

        List<FinancialMovement> rows = movementRepository.findAllByBankAccountIdOrdered(bankAccountId).stream()
                .filter(m -> desde == null || (m.getMovementDate() != null && !m.getMovementDate().isBefore(desde)))
                .filter(m -> hasta == null || (m.getMovementDate() != null && !m.getMovementDate().isAfter(hasta)))
                .toList();

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalFuncional = BigDecimal.ZERO, totalCierre = BigDecimal.ZERO, totalDif = BigDecimal.ZERO;
        for (FinancialMovement m : rows) {
            BigDecimal montoOriginal = m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO;
            BigDecimal trmApl = m.getTrmAplicada();
            BigDecimal funcional = m.getMontoFuncional();
            BigDecimal equivCierre = trmCierre != null ? montoOriginal.multiply(trmCierre).setScale(2, RoundingMode.HALF_UP) : null;
            BigDecimal diferencia = (equivCierre != null && funcional != null) ? equivCierre.subtract(funcional) : null;
            if (funcional != null) totalFuncional = totalFuncional.add(funcional);
            if (equivCierre != null) totalCierre = totalCierre.add(equivCierre);
            if (diferencia != null) totalDif = totalDif.add(diferencia);

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("movimientoId", m.getId());
            d.put("fecha", m.getMovementDate());
            d.put("descripcion", m.getDescription());
            d.put("montoOriginal", montoOriginal);
            d.put("trmAplicada", trmApl);
            d.put("montoFuncional", funcional);
            d.put("trmCierre", trmCierre);
            d.put("equivalenteCierre", equivCierre);
            d.put("diferencia", diferencia);
            d.put("estado", m.getEstadoConciliacion());
            items.add(d);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bankAccountId", bankAccountId);
        out.put("moneda", iso);
        out.put("trmCierre", trmCierre);
        out.put("movimientos", items);
        out.put("totalFuncional", totalFuncional);
        out.put("totalEquivalenteCierre", totalCierre);
        out.put("totalDiferencia", totalDif);
        return out;
    }

    /** HU-076 E7: exportar el reporte a Excel/CSV. */
    @SuppressWarnings("unchecked")
    public byte[] exportReporte(Long bankAccountId, LocalDate fechaCierre, LocalDate desde, LocalDate hasta, String format) {
        Map<String, Object> rep = reporte(bankAccountId, fechaCierre, desde, hasta);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) rep.get("movimientos");
        List<String> headers = List.of("Mov", "Fecha", "Descripción", "Monto " + rep.get("moneda"),
                "TRM aplicada", "Equiv. COP", "TRM cierre", "Equiv. cierre COP", "Diferencia", "Estado");
        List<Function<Map<String, Object>, Object>> cols = List.of(
                m -> m.get("movimientoId"), m -> m.get("fecha"), m -> m.get("descripcion"),
                m -> m.get("montoOriginal"), m -> m.get("trmAplicada"), m -> m.get("montoFuncional"),
                m -> m.get("trmCierre"), m -> m.get("equivalenteCierre"), m -> m.get("diferencia"),
                m -> m.get("estado"));
        if ("csv".equalsIgnoreCase(format)) {
            return SimpleTableExporter.toCsv(headers, cols, rows);
        }
        return SimpleTableExporter.toXlsx("MonedaExtranjera", headers, cols, rows);
    }

    // ===================== helpers =====================

    private BankAccount loadForeignAccount(Long bankAccountId) {
        BankAccount ba = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada."));
        if (ba.getCurrencyType() == null || ba.getCurrencyType().getIsoCode() == null
                || "COP".equalsIgnoreCase(ba.getCurrencyType().getIsoCode())) {
            throw new IllegalArgumentException("La cuenta no es en moneda extranjera; la diferencia en cambio solo aplica a cuentas con moneda distinta de COP.");
        }
        return ba;
    }

    private Long resolvePuc(String pucCode) {
        Long companyId = TenantContext.getCompanyId();
        return accountingAccountRepository.findActiveByPucCodeAndCompany(pucCode, companyId)
                .map(AccountingAccount::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "La cuenta PUC " + pucCode + " (diferencia en cambio) no existe o no está activa en el catálogo de la empresa."));
    }

    private CreateJournalEntryLineRequest line(Long accId, BigDecimal debit, BigDecimal credit, String desc) {
        return CreateJournalEntryLineRequest.builder()
                .accountingAccountId(accId).debitAmount(debit).creditAmount(credit).description(desc).build();
    }

    private String currentUser() {
        try { var u = userUtil.getUser(); return u != null ? u.getUsername() : "sistema"; }
        catch (Exception e) { return "sistema"; }
    }

    private String safeVoucher(JournalEntryDTO je) {
        try { return je.getVoucherCode(); } catch (Exception e) { return null; }
    }
}

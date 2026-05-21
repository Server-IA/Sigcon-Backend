package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.matching.application.GenerateAdjustmentRequest;
import com.sigcon.backend.banks.matching.application.GenerateBatchAdjustmentRequest;
import com.sigcon.backend.banks.matching.domain.model.Emparejamiento;
import com.sigcon.backend.banks.matching.domain.repository.EmparejamientoDetalleRepository;
import com.sigcon.backend.banks.matching.domain.repository.EmparejamientoRepository;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * BNK-HU-073: generación de comprobantes de ajuste de conciliación a partir de
 * partidas conciliatorias (movimientos del extracto sin contrapartida en libros:
 * GMF, comisión, intereses, notas débito/crédito).
 *
 * Reglas clave:
 *  - El comprobante se crea SIEMPRE en BORRADOR (HU-073 E4); el contador lo
 *    aprueba manualmente en Contabilidad General.
 *  - Por cada movimiento del extracto se crea un movimiento de libros + un
 *    emparejamiento 1:1 MANUAL score 100 (HU-073 E5), y el extracto queda
 *    CONCILIADO.
 *  - La partida conciliatoria pasa a RESUELTA_AJUSTE (HU-073 E8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustmentEntryService {

    private final FinancialMovementRepository movementRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final JournalEntryService journalEntryService;
    private final EmparejamientoRepository emparejamientoRepository;
    private final EmparejamientoDetalleRepository detalleRepository;
    private final PartidaConciliatoriaService partidaService;
    private final AuditPublisher auditPublisher;
    private final UserUtil userUtil;

    /** Cuentas resueltas (PUC + accounting_accounts.id) para un movimiento. */
    private static class Resolved {
        Long debitAccId, creditAccId;
        String debitPuc, creditPuc, tipoPartida;
    }

    /**
     * HU-073 E1/E2: preview del asiento propuesto SIN persistir nada (para que la
     * UI muestre las cuentas y el monto antes de confirmar).
     */
    public Map<String, Object> preview(GenerateAdjustmentRequest req) {
        FinancialMovement m = loadExtractMovement(req.getFinancialMovementId());
        BankAccount ba = m.getBankAccount();
        Resolved r = resolveAccounts(m, ba, req.getCuentaDebitoOverride(), req.getCuentaCreditoOverride());
        BigDecimal monto = m.getAmount() != null ? m.getAmount().abs() : BigDecimal.ZERO;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("financialMovementId", m.getId());
        out.put("fecha", m.getMovementDate());
        out.put("descripcion", m.getDescription());
        out.put("tipoMovimiento", m.getTipoMovimiento());
        out.put("monto", monto);
        out.put("cuentaDebitoPuc", r.debitPuc);
        out.put("cuentaCreditoPuc", r.creditPuc);
        out.put("cuentaDebitoNombre", nombreCuenta(r.debitAccId));
        out.put("cuentaCreditoNombre", nombreCuenta(r.creditAccId));
        return out;
    }

    /**
     * BNK-HU-073 E3-E9: genera el comprobante de ajuste para UN movimiento.
     */
    @Transactional
    public Map<String, Object> generate(GenerateAdjustmentRequest req) {
        User user = userUtil.getUser();
        String username = user != null ? user.getUsername() : "sistema";
        return doGenerateSingle(req.getFinancialMovementId(),
                req.getCuentaDebitoOverride(), req.getCuentaCreditoOverride(), username);
    }

    /**
     * BNK-HU-073 E6: generación en lote. modo UNICO = un comprobante con N líneas;
     * modo INDIVIDUAL = N comprobantes separados.
     */
    @Transactional
    public Map<String, Object> generateBatch(GenerateBatchAdjustmentRequest req) {
        User user = userUtil.getUser();
        String username = user != null ? user.getUsername() : "sistema";
        String modo = (req.getModo() == null || req.getModo().isBlank()) ? "UNICO" : req.getModo().toUpperCase();

        if ("INDIVIDUAL".equals(modo)) {
            List<Object> resultados = new ArrayList<>();
            for (Long movId : req.getFinancialMovementIds()) {
                resultados.add(doGenerateSingle(movId, null, null, username));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("modo", "INDIVIDUAL");
            out.put("comprobantesCreados", resultados.size());
            out.put("detalle", resultados);
            return out;
        }

        // ---- modo UNICO: un solo comprobante con N pares de líneas ----
        List<FinancialMovement> movs = new ArrayList<>();
        BankAccount ba = bankAccountRepository.findById(req.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));
        List<CreateJournalEntryLineRequest> lines = new ArrayList<>();
        List<Resolved> resolvedList = new ArrayList<>();
        List<String> omitidos = new ArrayList<>();

        for (Long movId : req.getFinancialMovementIds()) {
            FinancialMovement m = loadExtractMovement(movId);
            Resolved r;
            try { r = resolveAccounts(m, ba, null, null); }
            catch (RuntimeException ex) { omitidos.add("#" + movId + ": " + ex.getMessage()); continue; }
            BigDecimal monto = m.getAmount() != null ? m.getAmount().abs() : BigDecimal.ZERO;
            lines.add(line(r.debitAccId, monto, BigDecimal.ZERO, "Ajuste " + m.getTipoMovimiento() + " mov #" + movId));
            lines.add(line(r.creditAccId, BigDecimal.ZERO, monto, "Ajuste " + m.getTipoMovimiento() + " mov #" + movId));
            movs.add(m);
            resolvedList.add(r);
        }
        if (movs.isEmpty()) {
            throw new IllegalArgumentException("Ningún movimiento es una partida de ajuste válida. " + String.join(" | ", omitidos));
        }

        CreateJournalEntryRequest jeReq = CreateJournalEntryRequest.builder()
                .entryDate(movs.get(0).getMovementDate())
                .description("Ajuste conciliación en lote (" + movs.size() + " movimientos) - cuenta " + ba.getId())
                .sourceModule(JournalSourceModule.BNK)
                .sourceId(ba.getId())
                .lines(lines)
                .build();
        validateDoubleEntry(lines); // HU-073 E7
        JournalEntryDTO je = journalEntryService.createEntry(jeReq, username);

        for (int i = 0; i < movs.size(); i++) {
            FinancialMovement m = movs.get(i);
            Resolved r = resolvedList.get(i);
            linkMovementToBooks(m, je.getId(), username);
            partidaService.resolveByAdjustment(m.getId(), ba.getId(), ba.getCompanyId(),
                    r.tipoPartida, m.getAmount(), je.getId(), r.debitPuc, r.creditPuc,
                    m.getDescripcionNormalizada() != null ? m.getDescripcionNormalizada() : m.getDescription());
        }
        auditPublisher.publish(AuditAction.CREATE, AuditModule.BNK, AuditSeverity.MEDIUM,
                "JournalEntry", je.getId(),
                "Ajuste conciliación EN LOTE (" + movs.size() + " mov) cuenta=" + ba.getId(),
                null, "{\"lineas\":" + lines.size() + ",\"movimientos\":" + movs.size() + "}", je.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modo", "UNICO");
        out.put("comprobanteId", je.getId());
        out.put("voucherCode", safeVoucher(je));
        out.put("movimientosConciliados", movs.size());
        out.put("lineas", lines.size());
        if (!omitidos.isEmpty()) out.put("omitidos", omitidos);
        return out;
    }

    // ===================== helpers =====================

    private Map<String, Object> doGenerateSingle(Long movId, String dbOverride, String crOverride, String username) {
        FinancialMovement m = loadExtractMovement(movId);
        BankAccount ba = m.getBankAccount();
        Resolved r = resolveAccounts(m, ba, dbOverride, crOverride);
        BigDecimal monto = m.getAmount() != null ? m.getAmount().abs() : BigDecimal.ZERO;

        // HU-073 E3: comprobante AJUSTE en BORRADOR, fecha = fecha del movimiento.
        String desc = "Ajuste conciliación [" + m.getId() + "] - "
                + (m.getDescripcionNormalizada() != null ? m.getDescripcionNormalizada() : m.getDescription());
        List<CreateJournalEntryLineRequest> lines = new ArrayList<>();
        lines.add(line(r.debitAccId, monto, BigDecimal.ZERO, desc));
        lines.add(line(r.creditAccId, BigDecimal.ZERO, monto, desc));
        validateDoubleEntry(lines); // HU-073 E7

        CreateJournalEntryRequest jeReq = CreateJournalEntryRequest.builder()
                .entryDate(m.getMovementDate())
                .description(desc)
                .sourceModule(JournalSourceModule.BNK)
                .sourceId(m.getId())
                .lines(lines)
                .build();
        // HU-073 E4: createEntry deja el comprobante en BORRADOR; NUNCA se aprueba aquí.
        JournalEntryDTO je = journalEntryService.createEntry(jeReq, username);

        // HU-073 E5: movimiento de libros + emparejamiento 1:1.
        linkMovementToBooks(m, je.getId(), username);

        // HU-073 E8: resolver la partida conciliatoria.
        partidaService.resolveByAdjustment(m.getId(), ba.getId(), ba.getCompanyId(),
                r.tipoPartida, m.getAmount(), je.getId(), r.debitPuc, r.creditPuc, desc);

        // HU-073 E9: auditar la creación del asiento con las líneas en valores_despues.
        String newValues = "{\"debito\":{\"cuenta\":\"" + r.debitPuc + "\",\"monto\":" + monto + "},"
                + "\"credito\":{\"cuenta\":\"" + r.creditPuc + "\",\"monto\":" + monto + "},"
                + "\"movimientoExtracto\":" + m.getId() + "}";
        auditPublisher.publish(AuditAction.CREATE, AuditModule.BNK, AuditSeverity.MEDIUM,
                "JournalEntry", je.getId(), "Ajuste conciliación mov #" + m.getId(),
                null, newValues, je.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("comprobanteId", je.getId());
        out.put("voucherCode", safeVoucher(je));
        out.put("financialMovementId", m.getId());
        out.put("monto", monto);
        out.put("cuentaDebitoPuc", r.debitPuc);
        out.put("cuentaCreditoPuc", r.creditPuc);
        out.put("estadoComprobante", "BORRADOR");
        return out;
    }

    /** HU-073 E5: crea el movimiento de libros y el emparejamiento 1:1 MANUAL. */
    private void linkMovementToBooks(FinancialMovement extractMov, Long jeId, String username) {
        BankAccount ba = extractMov.getBankAccount();
        FinancialMovement libros = FinancialMovement.builder()
                .companyId(ba.getCompanyId())
                .bankAccount(ba)
                .movementDate(extractMov.getMovementDate())
                .amount(extractMov.getAmount()) // mismo signo: refleja el efecto en libros
                .description("Ajuste conciliación (comprobante #" + jeId + ")")
                .sourceType(FinancialMovementSourceType.MANUAL)
                .flowActivity("OPERATIVA")
                .tipoMovimiento("AJUSTE_CONCILIACION")
                .estadoConciliacion("CONCILIADO")
                .matchedJournalEntryId(jeId)
                .build();
        libros = movementRepository.save(libros);

        BigDecimal abs = extractMov.getAmount() != null ? extractMov.getAmount().abs() : BigDecimal.ZERO;
        Emparejamiento emp = Emparejamiento.builder()
                .companyId(ba.getCompanyId())
                .cuentaBancariaId(ba.getId())
                .tipoEmparejamiento("UNO_A_UNO")
                .metodo("MANUAL")
                .score(100)
                .estado("CONFIRMADO")
                .sumaExtracto(abs)
                .sumaLibros(abs)
                .diferencia(BigDecimal.ZERO)
                .motivoMatchManual("Ajuste de conciliación (comprobante #" + jeId + ")")
                .confirmadoAt(LocalDateTime.now())
                .confirmadoBy(username)
                .build();
        emp = emparejamientoRepository.save(emp);
        saveDetalle(emp, extractMov, "EXTRACTO");
        saveDetalle(emp, libros, "LIBROS");

        extractMov.setEstadoConciliacion("CONCILIADO");
        movementRepository.save(extractMov);
    }

    private void saveDetalle(Emparejamiento emp, FinancialMovement m, String lado) {
        detalleRepository.save(com.sigcon.backend.banks.matching.domain.model.EmparejamientoDetalle.builder()
                .companyId(emp.getCompanyId())
                .emparejamientoId(emp.getId())
                .financialMovementId(m.getId())
                .lado(lado)
                .monto(m.getAmount())
                .build());
    }

    private Resolved resolveAccounts(FinancialMovement m, BankAccount ba, String dbOverride, String crOverride) {
        String bankPuc = (ba.getAccountingAccount() != null && ba.getAccountingAccount().getPucAccount() != null)
                ? ba.getAccountingAccount().getPucAccount().getCode() : null;
        boolean aplicaGmf = Boolean.TRUE.equals(ba.getAplicaGmf());
        PartidaConciliatoriaService.AdjMap map = partidaService.mapFor(m.getTipoMovimiento(), bankPuc, m.getCuentaPucSugerida(), aplicaGmf);
        if (map.gmfExento) {
            throw new IllegalStateException("La cuenta es exenta de GMF (art. 879 ET); no se genera ajuste de GMF. "
                    + "Si el banco lo cobró por error, regístrelo manualmente.");
        }
        String dbPuc = (dbOverride != null && !dbOverride.isBlank()) ? dbOverride.trim() : map.cuentaDebito;
        String crPuc = (crOverride != null && !crOverride.isBlank()) ? crOverride.trim() : map.cuentaCredito;
        if (dbPuc == null || crPuc == null) {
            throw new IllegalArgumentException("No se pudo determinar la cuenta contrapartida del ajuste. "
                    + "El movimiento (tipo " + m.getTipoMovimiento() + ") no es un ajuste reconocido; "
                    + "indique las cuentas de débito y crédito manualmente.");
        }
        Resolved r = new Resolved();
        r.debitPuc = dbPuc;
        r.creditPuc = crPuc;
        r.tipoPartida = map.tipoPartida;
        r.debitAccId = resolvePucToAccount(dbPuc);
        r.creditAccId = resolvePucToAccount(crPuc);
        return r;
    }

    /** HU-073 E2: valida que la cuenta PUC exista y esté activa en el catálogo de la empresa. */
    private Long resolvePucToAccount(String pucCode) {
        Long companyId = TenantContext.getCompanyId();
        return accountingAccountRepository.findActiveByPucCodeAndCompany(pucCode, companyId)
                .map(AccountingAccount::getId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La cuenta PUC " + pucCode + " no existe o no está activa en el catálogo de cuentas de la empresa. "
                        + "Créela en Listas Contables antes de generar el ajuste."));
    }

    private FinancialMovement loadExtractMovement(Long movId) {
        FinancialMovement m = movementRepository.findById(movId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado: " + movId));
        if (m.getSourceType() != FinancialMovementSourceType.BANK_IMPORT) {
            throw new IllegalArgumentException("Solo se generan ajustes para movimientos del extracto (importados del banco).");
        }
        if ("CONCILIADO".equals(m.getEstadoConciliacion())) {
            throw new IllegalStateException("El movimiento #" + movId + " ya está conciliado.");
        }
        return m;
    }

    private CreateJournalEntryLineRequest line(Long accId, BigDecimal debit, BigDecimal credit, String desc) {
        return CreateJournalEntryLineRequest.builder()
                .accountingAccountId(accId)
                .debitAmount(debit)
                .creditAmount(credit)
                .description(desc)
                .build();
    }

    /** HU-073 E7: valida Σ débitos = Σ créditos con tolerancia $0.01. */
    private void validateDoubleEntry(List<CreateJournalEntryLineRequest> lines) {
        BigDecimal d = BigDecimal.ZERO, c = BigDecimal.ZERO;
        for (CreateJournalEntryLineRequest l : lines) {
            d = d.add(l.getDebitAmount() != null ? l.getDebitAmount() : BigDecimal.ZERO);
            c = c.add(l.getCreditAmount() != null ? l.getCreditAmount() : BigDecimal.ZERO);
        }
        if (d.subtract(c).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new IllegalStateException("Error técnico: partida doble descuadrada (débitos $" + d + " != créditos $" + c + ").");
        }
    }

    private String nombreCuenta(Long accId) {
        if (accId == null) return null;
        return accountingAccountRepository.findById(accId)
                .map(a -> a.getPucAccount() != null ? a.getPucAccount().getName() : a.getCustomName())
                .orElse(null);
    }

    private String safeVoucher(JournalEntryDTO je) {
        try { return je.getVoucherCode(); } catch (Exception e) { return null; }
    }
}

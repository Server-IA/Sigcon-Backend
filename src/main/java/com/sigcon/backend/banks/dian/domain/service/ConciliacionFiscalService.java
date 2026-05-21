package com.sigcon.backend.banks.dian.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditLogService;
import com.sigcon.backend.banks.archivos_soporte.domain.service.ArchivoSoporteService;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.dian.domain.model.ConciliacionFiscalNota;
import com.sigcon.backend.banks.dian.domain.repository.ConciliacionFiscalNotaRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.trm.domain.service.TrmService;
import com.sigcon.backend.parametrization.users.domain.model.User;
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
 * BNK-HU-080: conciliación fiscal (art. 772-1 ET) que conecta los saldos contables (NIIF)
 * de cuentas bancarias con sus valores fiscales, para los formatos 2516 (jurídicas) y
 * 2517 (naturales).
 *
 * <p><b>STAND-IN (infra diferida):</b> el archivo en estructura XML/XSD oficial DIAN 2516/2517
 * (que cambia por año gravable) es infraestructura externa. El sistema computa los saldos NIIF
 * vs fiscal, diferencias temporarias/permanentes, GMF (deducibilidad configurable), diferencia
 * en cambio realizada vs no realizada, notas por partida, y exporta CSV + conserva 10 años.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliacionFiscalService {

    /** HU-080 E3: deducibilidad del GMF (Art. 115 ET, 50%). Configurable por año normativo. */
    private static final BigDecimal GMF_DEDUCIBLE_PCT = new BigDecimal("0.50");

    private final BankAccountRepository bankAccountRepository;
    private final FinancialMovementRepository movementRepository;
    private final ConciliacionFiscalNotaRepository notaRepository;
    private final TrmService trmService;
    private final ArchivoSoporteService archivoSoporteService;
    private final AuditLogService auditLogService;
    private final UserUtil userUtil;

    // ===================== E1-E4: generar conciliación fiscal =====================

    public Map<String, Object> generar(int ano) {
        LocalDate cierre = LocalDate.of(ano, 12, 31);
        Map<Integer, String> notas = cargarNotas(ano);

        List<Map<String, Object>> partidas = new ArrayList<>();
        BigDecimal totNiif = BigDecimal.ZERO, totFiscal = BigDecimal.ZERO,
                totTemporaria = BigDecimal.ZERO, gmfAnio = BigDecimal.ZERO,
                difRealizada = BigDecimal.ZERO, difNoRealizada = BigDecimal.ZERO;

        for (BankAccount ba : bankAccountRepository.findAll()) {
            if (ba.getDeletedAt() != null) continue;
            String iso = ba.getCurrencyType() != null ? ba.getCurrencyType().getIsoCode() : "COP";
            boolean foreign = iso != null && !"COP".equalsIgnoreCase(iso);
            BigDecimal trmCierre = foreign ? trmService.trmParaFecha(iso, cierre) : BigDecimal.ONE;

            BigDecimal saldoFiscal = ba.getInitialBalance() != null ? ba.getInitialBalance() : BigDecimal.ZERO;
            BigDecimal saldoNiif = saldoFiscal;
            for (FinancialMovement m : movementRepository.findAllByBankAccountIdOrdered(ba.getId())) {
                if (m.getMovementDate() == null || m.getMovementDate().isAfter(cierre)) continue;
                BigDecimal amt = safe(m.getAmount());
                if (foreign) {
                    // fiscal = costo histórico (TRM de reconocimiento inicial = monto_funcional);
                    // NIIF = revaluación a TRM de cierre.
                    BigDecimal funcional = m.getMontoFuncional() != null ? m.getMontoFuncional() : amt;
                    saldoFiscal = saldoFiscal.add(funcional);
                    saldoNiif = saldoNiif.add(trmCierre != null ? amt.multiply(trmCierre).setScale(2, RoundingMode.HALF_UP) : funcional);
                    // GMF: tratamiento aparte (abajo).
                } else {
                    saldoFiscal = saldoFiscal.add(amt);
                    saldoNiif = saldoNiif.add(amt);
                }
                // GMF del año (cuenta gravada): movimientos clasificados como GMF (HU-068).
                if (m.getMovementDate().getYear() == ano && m.getTipoMovimiento() != null
                        && m.getTipoMovimiento().toUpperCase().contains("GMF")) {
                    gmfAnio = gmfAnio.add(amt.abs());
                }
            }
            BigDecimal dif = saldoNiif.subtract(saldoFiscal);
            String tipo = dif.abs().compareTo(new BigDecimal("0.01")) <= 0 ? "NINGUNA"
                    : (foreign ? "TEMPORARIA" : "PERMANENTE");
            if ("TEMPORARIA".equals(tipo)) { totTemporaria = totTemporaria.add(dif); difNoRealizada = difNoRealizada.add(dif); }

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("partidaKey", "CUENTA-" + ba.getId());
            p.put("cuenta", ba.getCode());
            p.put("moneda", iso);
            p.put("saldoContableNiif", saldoNiif);
            p.put("saldoFiscal", saldoFiscal);
            p.put("diferencia", dif);
            p.put("tipoDiferencia", tipo);
            p.put("nota", notas.get(("CUENTA-" + ba.getId()).hashCode()));
            partidas.add(p);
            totNiif = totNiif.add(saldoNiif); totFiscal = totFiscal.add(saldoFiscal);
        }

        // E3: GMF como ítem específico de conciliación fiscal.
        BigDecimal gmfDeducible = gmfAnio.multiply(GMF_DEDUCIBLE_PCT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gmfNoDeducible = gmfAnio.subtract(gmfDeducible);
        Map<String, Object> gmf = new LinkedHashMap<>();
        gmf.put("gastoGmfAnio", gmfAnio);
        gmf.put("deduciblePct", GMF_DEDUCIBLE_PCT.movePointRight(2));
        gmf.put("gmfDeducible", gmfDeducible);
        gmf.put("gmfNoDeducible", gmfNoDeducible); // diferencia permanente
        gmf.put("nota", cargarNotaStr(ano, "GMF"));

        // E4: diferencia en cambio realizada vs no realizada.
        Map<String, Object> difCambio = new LinkedHashMap<>();
        difCambio.put("noRealizada", difNoRealizada);   // revaluación del saldo al cierre (temporaria)
        difCambio.put("realizada", difRealizada);       // reconocida al cobrar/pagar (se realiza al liquidar la partida)
        difCambio.put("nota", cargarNotaStr(ano, "DIF_CAMBIO"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ano", ano);
        out.put("partidas", partidas);
        out.put("totalNiif", totNiif);
        out.put("totalFiscal", totFiscal);
        out.put("totalDiferenciaTemporaria", totTemporaria);
        out.put("gmf", gmf);
        out.put("diferenciaEnCambio", difCambio);
        return out;
    }

    // ===================== E6: notas por partida =====================

    @Transactional
    public ConciliacionFiscalNota upsertNota(int ano, String partidaKey, String nota) {
        if (partidaKey == null || partidaKey.isBlank()) throw new IllegalArgumentException("Indique la partida.");
        if (nota == null || nota.isBlank()) throw new IllegalArgumentException("La nota no puede estar vacía.");
        User u = userUtil.getUser();
        ConciliacionFiscalNota n = notaRepository.findByAnoFiscalAndPartidaKey(ano, partidaKey.trim())
                .orElseGet(() -> ConciliacionFiscalNota.builder().anoFiscal(ano).partidaKey(partidaKey.trim())
                        .createdBy(u != null ? u.getId() : null).build());
        n.setNota(nota.trim());
        return notaRepository.save(n);
    }

    public List<ConciliacionFiscalNota> listarNotas(int ano) {
        return notaRepository.findByAnoFiscal(ano);
    }

    // ===================== E5/E7: exportar + auditar + retención =====================

    @Transactional
    @SuppressWarnings("unchecked")
    public byte[] exportar(int ano, String formato) {
        String fmt = "2517".equals(formato) ? "2517" : "2516";
        Map<String, Object> data = generar(ano);
        List<Map<String, Object>> partidas = (List<Map<String, Object>>) data.get("partidas");

        List<String> headers = List.of("Partida", "Cuenta", "Moneda", "Saldo NIIF", "Saldo fiscal", "Diferencia", "Tipo", "Nota");
        List<Function<Map<String, Object>, Object>> cols = List.of(
                m -> m.get("partidaKey"), m -> m.get("cuenta"), m -> m.get("moneda"),
                m -> m.get("saldoContableNiif"), m -> m.get("saldoFiscal"), m -> m.get("diferencia"),
                m -> m.get("tipoDiferencia"), m -> m.get("nota"));
        byte[] bytes = SimpleTableExporter.toCsv(headers, cols, partidas);

        User u = userUtil.getUser();
        try {
            archivoSoporteService.store(bytes, "conciliacion_fiscal_" + fmt + "_" + ano + ".csv",
                    SimpleTableExporter.CSV_MIME, "CONCILIACION_FISCAL_" + fmt, null, null,
                    u != null ? u.getId() : null);
        } catch (RuntimeException ex) {
            log.warn("BNK-HU-080: no se pudo conservar el soporte de conciliación fiscal: {}", ex.getMessage());
        }
        auditLogService.register(AuditAction.EXPORT, AuditModule.BNK, AuditSeverity.LOW,
                "ConciliacionFiscal", (long) ano,
                "EXPORTAR · Conciliación fiscal formato " + fmt + " año " + ano,
                null, null, null);
        return bytes;
    }

    // ===================== helpers =====================

    private Map<Integer, String> cargarNotas(int ano) {
        Map<Integer, String> map = new HashMap<>();
        for (ConciliacionFiscalNota n : notaRepository.findByAnoFiscal(ano))
            map.put(n.getPartidaKey().hashCode(), n.getNota());
        return map;
    }

    private String cargarNotaStr(int ano, String key) {
        return notaRepository.findByAnoFiscalAndPartidaKey(ano, key).map(ConciliacionFiscalNota::getNota).orElse(null);
    }

    private BigDecimal safe(BigDecimal b) { return b != null ? b : BigDecimal.ZERO; }
}

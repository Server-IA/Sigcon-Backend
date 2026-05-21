package com.sigcon.backend.banks.trm.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditLogService;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.trm.domain.model.ConfigTrm;
import com.sigcon.backend.banks.trm.domain.model.TrmHistorica;
import com.sigcon.backend.banks.trm.domain.repository.ConfigTrmRepository;
import com.sigcon.backend.banks.trm.domain.repository.TrmHistoricaRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * BNK-HU-076: gestión de la TRM (Tasa Representativa del Mercado) y conversión dual
 * de montos para cuentas en moneda extranjera (NIC 21).
 *
 * <p><b>Stand-in del fetch oficial (HU-076 E1):</b> conectarse al servicio de la
 * Superintendencia Financiera es infraestructura externa (diferido). El sistema soporta:
 * <ul>
 *   <li>Carga MANUAL de la TRM por el contador (registrarTrm).</li>
 *   <li>Un job diario que, si no hay TRM del día para una moneda en uso, arrastra la
 *       última publicada (fuente=ULTIMA_PUBLICADA) y emite alerta al admin — exactamente
 *       el comportamiento de fallback que pide la HU cuando "la fuente no responde".</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrmService {

    private final TrmHistoricaRepository trmRepository;
    private final ConfigTrmRepository configRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    // ===================== Carga / consulta de TRM =====================

    /**
     * HU-076 E1 (manual): registra/actualiza la TRM de una moneda en una fecha.
     * Upsert por (empresa, moneda, fecha): si ya existe, actualiza el valor.
     */
    @Transactional
    public TrmHistorica registrarTrm(String currencyIso, LocalDate fecha, BigDecimal valorCop,
                                     String fuente, Long userId) {
        if (currencyIso == null || currencyIso.isBlank())
            throw new IllegalArgumentException("Debe indicar la moneda (código ISO).");
        String iso = currencyIso.trim().toUpperCase();
        if ("COP".equals(iso))
            throw new IllegalArgumentException("La TRM aplica a monedas extranjeras; COP es la moneda funcional (TRM = 1).");
        if (!currencyTypeRepository.existsByIsoCodeAndDeletedAtIsNull(iso))
            throw new IllegalArgumentException("La moneda " + iso + " no está registrada en el catálogo de monedas.");
        if (fecha == null) throw new IllegalArgumentException("Debe indicar la fecha de la TRM.");
        if (fecha.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("La fecha de la TRM no puede ser futura.");
        if (valorCop == null || valorCop.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("El valor de la TRM debe ser mayor a cero.");

        Optional<TrmHistorica> existing = trmRepository.findByCurrencyIsoAndFechaAndDeletedAtIsNull(iso, fecha);
        TrmHistorica trm;
        if (existing.isPresent()) {
            trm = existing.get();
            trm.setValorCop(valorCop);
            trm.setFuente(fuente != null && !fuente.isBlank() ? fuente.trim().toUpperCase() : "MANUAL");
        } else {
            trm = TrmHistorica.builder()
                    .fecha(fecha)
                    .currencyIso(iso)
                    .valorCop(valorCop)
                    .fuente(fuente != null && !fuente.isBlank() ? fuente.trim().toUpperCase() : "MANUAL")
                    .createdBy(userId)
                    .build();
        }
        trm = trmRepository.save(trm);
        auditLogService.register(AuditAction.CREATE, AuditModule.BNK, AuditSeverity.LOW,
                "TrmHistorica", trm.getId(),
                "TRM " + iso + " " + fecha + " = $" + valorCop + " (" + trm.getFuente() + ")",
                null, null, null);
        return trm;
    }

    /** Histórico de una moneda (opcionalmente en rango de fechas). */
    public List<TrmHistorica> historica(String currencyIso, LocalDate desde, LocalDate hasta) {
        String iso = currencyIso.trim().toUpperCase();
        if (desde != null && hasta != null)
            return trmRepository.findByCurrencyIsoAndFechaBetweenAndDeletedAtIsNullOrderByFechaDesc(iso, desde, hasta);
        return trmRepository.findByCurrencyIsoAndDeletedAtIsNullOrderByFechaDesc(iso);
    }

    /**
     * HU-076 E2/E5: TRM vigente para una fecha = la más reciente con fecha &lt;= la dada.
     * Devuelve null si no hay ninguna TRM cargada hasta esa fecha.
     */
    public BigDecimal trmParaFecha(String currencyIso, LocalDate fecha) {
        if (currencyIso == null) return null;
        String iso = currencyIso.trim().toUpperCase();
        if ("COP".equals(iso)) return BigDecimal.ONE;
        return trmRepository
                .findTopByCurrencyIsoAndFechaLessThanEqualAndDeletedAtIsNullOrderByFechaDesc(iso, fecha)
                .map(TrmHistorica::getValorCop)
                .orElse(null);
    }

    /** HU-076 E8: monedas soportadas = catálogo de monedas activas distintas de COP. */
    public List<Map<String, Object>> monedasSoportadas() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CurrencyType c : currencyTypeRepository.findAll()) {
            if (c.getIsoCode() == null || "COP".equalsIgnoreCase(c.getIsoCode())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("isoCode", c.getIsoCode());
            m.put("name", c.getName());
            out.add(m);
        }
        return out;
    }

    // ===================== Política TRM (HU-076 E8) =====================

    @Transactional
    public ConfigTrm getOrCreateConfig() {
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null) throw new IllegalStateException("Se requiere empresa activa.");
        return configRepository.findByCompanyId(companyId)
                .orElseGet(() -> configRepository.save(ConfigTrm.builder()
                        .companyId(companyId).politicaTrm("FECHA_MOVIMIENTO").build()));
    }

    @Transactional
    public ConfigTrm updateConfig(String politicaTrm) {
        if (politicaTrm == null || !Set.of("FECHA_MOVIMIENTO", "FECHA_CIERRE").contains(politicaTrm.trim().toUpperCase()))
            throw new IllegalArgumentException("Política inválida. Use FECHA_MOVIMIENTO o FECHA_CIERRE.");
        ConfigTrm cfg = getOrCreateConfig();
        cfg.setPoliticaTrm(politicaTrm.trim().toUpperCase());
        return configRepository.save(cfg);
    }

    // ===================== Aplicación a movimientos (HU-076 E2) =====================

    /**
     * HU-076 E2: rellena monto_funcional y trm_aplicada de un movimiento según la moneda
     * de su cuenta. Para cuentas COP: trm=1, funcional=amount. Para extranjeras: usa la
     * TRM de la fecha del movimiento; si no hay TRM cargada, deja los campos en null
     * (el reporte/cálculo de diferencia lo marcará).
     *
     * <p>Es defensivo: nunca lanza, para no bloquear la creación/importación del movimiento.
     */
    public void aplicarTrm(FinancialMovement m) {
        try {
            BankAccount ba = m.getBankAccount();
            if (ba == null || ba.getCurrencyType() == null) return;
            String iso = ba.getCurrencyType().getIsoCode();
            if (iso == null || "COP".equalsIgnoreCase(iso)) {
                m.setTrmAplicada(BigDecimal.ONE);
                m.setMontoFuncional(m.getAmount());
                return;
            }
            BigDecimal trm = trmParaFecha(iso, m.getMovementDate());
            if (trm == null) {
                log.warn("BNK-HU-076: no hay TRM cargada para {} a la fecha {} (movimiento sin monto funcional).",
                        iso, m.getMovementDate());
                return;
            }
            m.setTrmAplicada(trm);
            m.setMontoFuncional(m.getAmount().multiply(trm).setScale(2, RoundingMode.HALF_UP));
        } catch (RuntimeException ex) {
            log.warn("BNK-HU-076: error aplicando TRM al movimiento: {}", ex.getMessage());
        }
    }

    // ===================== Job carry-forward (HU-076 E1 fallback) =====================

    /**
     * HU-076 E1 (fallback): job diario a las 04:00 AM. El fetch al servicio oficial de la
     * Super es infraestructura externa (diferido). Mientras tanto, este job implementa el
     * comportamiento de respaldo que pide la HU: si una moneda en uso NO tiene TRM del día,
     * arrastra la última publicada (fuente=ULTIMA_PUBLICADA) y emite una alerta al admin.
     */
    @Scheduled(cron = "${sigcon.bnk.trm-cron:0 0 4 * * MON-FRI}")
    public void dailyTrmJob() {
        try {
            List<Company> companies = companyRepository.findAll();
            for (Company c : companies) {
                if (c.getDeletedAt() != null) continue;
                TenantContext.runAs(c.getId(), false, () -> carryForwardForCurrentTenant(LocalDate.now()));
            }
        } catch (RuntimeException ex) {
            log.error("BNK-HU-076: job diario de TRM falló: {}", ex.getMessage(), ex);
        }
    }

    /** Arrastra la última TRM publicada a {@code fecha} para cada moneda en uso que no tenga dato del día. */
    @Transactional
    public Map<String, Object> carryForwardForCurrentTenant(LocalDate fecha) {
        List<String> arrastradas = new ArrayList<>();
        List<String> sinHistorico = new ArrayList<>();
        for (String iso : trmRepository.findDistinctCurrencies()) {
            boolean tieneHoy = trmRepository.findByCurrencyIsoAndFechaAndDeletedAtIsNull(iso, fecha).isPresent();
            if (tieneHoy) continue;
            Optional<TrmHistorica> ultima = trmRepository.findTopByCurrencyIsoAndDeletedAtIsNullOrderByFechaDesc(iso);
            if (ultima.isEmpty()) { sinHistorico.add(iso); continue; }
            TrmHistorica nueva = TrmHistorica.builder()
                    .fecha(fecha).currencyIso(iso).valorCop(ultima.get().getValorCop())
                    .fuente("ULTIMA_PUBLICADA").build();
            trmRepository.save(nueva);
            arrastradas.add(iso + "=$" + ultima.get().getValorCop());
            // Alerta al administrador (stand-in del correo: queda en el log de auditoría).
            auditLogService.register(AuditAction.UPDATE, AuditModule.BNK, AuditSeverity.MEDIUM,
                    "TrmHistorica", nueva.getId(),
                    "TRM_FUENTE_NO_DISPONIBLE: se usó la última TRM publicada de " + iso
                            + " ($" + ultima.get().getValorCop() + ") para " + fecha
                            + ". Verifique la TRM oficial del día.",
                    null, null, null);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fecha", fecha);
        out.put("arrastradas", arrastradas);
        out.put("sinHistorico", sinHistorico);
        return out;
    }
}

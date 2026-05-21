package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.matching.domain.model.ReglaClasificacion;
import com.sigcon.backend.banks.matching.domain.repository.ReglaClasificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BNK-HU-068: pre-procesamiento de movimientos del extracto — normaliza la
 * descripción, extrae referencias (cheque, PSE, NIT) por regex y clasifica el
 * movimiento aplicando las reglas configurables (HU-071) en cascada por prioridad.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreprocessingService {

    private static final Pattern P_CHEQUE = Pattern.compile("CHQ?\\s*(\\d+)|CK\\s*(\\d+)|CHEQUE\\s*(\\d+)");
    private static final Pattern P_REF = Pattern.compile("PSE\\s+(\\d+)|REF\\s+(\\d+)|COMP\\s*(\\d+)");
    private static final Pattern P_NIT = Pattern.compile("NIT\\s*(\\d{9,10})");

    private final FinancialMovementRepository movementRepository;
    private final ReglaClasificacionRepository reglaRepository;
    private final AuditPublisher auditPublisher;
    private final PartidaConciliatoriaService partidaService;

    /**
     * BNK-HU-068 E1: pre-procesa los movimientos de la cuenta que aún no han sido
     * normalizados (descripcion_normalizada IS NULL).
     */
    @Transactional
    public Map<String, Object> preprocessAccount(Long bankAccountId) {
        List<ReglaClasificacion> reglas = reglaRepository.findByActivaTrueAndDeletedAtIsNullOrderByPrioridadAsc();
        // Pre-compilar patrones de reglas (con guard de sintaxis).
        for (ReglaClasificacion r : reglas) {
            try { r.setPatronRegex(r.getPatronRegex()); } catch (Exception ignored) {}
        }

        List<FinancialMovement> movs = movementRepository.findAllByBankAccountIdOrdered(bankAccountId);
        int procesados = 0, clasificados = 0, bajaConfianza = 0;
        Long bankId = null;
        for (FinancialMovement m : movs) {
            if (m.getDescripcionNormalizada() != null) continue; // ya pre-procesado
            if (bankId == null && m.getBankAccount() != null && m.getBankAccount().getBank() != null) {
                bankId = m.getBankAccount().getBank().getId();
            }
            preprocessOne(m, reglas, bankAccountId, bankId);
            procesados++;
            if (m.getClasificacionConfianza() != null && m.getClasificacionConfianza() >= 90) clasificados++;
            else bajaConfianza++;
            movementRepository.save(m);
        }
        auditPublisher.publishUpdate(AuditModule.BNK, "FinancialMovement", bankAccountId,
                "PRE-PROCESAMIENTO cuenta=" + bankAccountId + " | procesados=" + procesados
                        + " | clasificados=" + clasificados + " | baja_confianza=" + bajaConfianza);

        // BNK-HU-061 E1: marca como PENDIENTE las partidas conciliatorias detectadas
        // (GMF/COMISION/INTERES_*/NOTA_*) y recoge alertas de GMF en cuentas exentas (E5).
        Map<String, Object> partidas = partidaService.ensureCandidatesForAccount(bankAccountId);

        Map<String, Object> r = new HashMap<>();
        r.put("procesados", procesados);
        r.put("clasificados", clasificados);
        r.put("bajaConfianza", bajaConfianza);
        r.put("partidasCreadas", partidas.get("partidasCreadas"));
        r.put("alertasGmfExento", partidas.get("alertasGmfExento"));
        return r;
    }

    /** Normaliza, extrae referencias y clasifica un movimiento. */
    public void preprocessOne(FinancialMovement m, List<ReglaClasificacion> reglas, Long bankAccountId, Long bankId) {
        // E2: normalización
        String norm = normalize(m.getDescription());
        m.setDescripcionNormalizada(norm);

        // E3/E4/E5: extracción por regex
        String cheque = firstGroup(P_CHEQUE.matcher(norm));
        if (cheque != null) m.setNumeroCheque(cheque);
        if (m.getExternalReference() == null) {
            String ref = firstGroup(P_REF.matcher(norm));
            if (ref != null) m.setExternalReference(ref);
        }
        String nit = firstGroup(P_NIT.matcher(norm));
        if (nit != null) m.setNitDetectado(nit);

        // E6: clasificación por reglas en cascada (prioridad ASC, primera que coincide)
        boolean matched = false;
        for (ReglaClasificacion r : reglas) {
            if (!aplicaAlcance(r, bankAccountId, bankId)) continue;
            if (!signoCoincide(r.getSigno(), m.getAmount())) continue;
            if (!montoEnRango(r, m.getAmount())) continue;
            Pattern p;
            try { p = Pattern.compile(r.getPatronRegex(), Pattern.CASE_INSENSITIVE); }
            catch (RuntimeException ex) { continue; }
            if (p.matcher(norm).find()) {
                m.setTipoMovimiento(r.getTipoMovimiento());
                m.setCuentaPucSugerida(r.getCuentaPucSugerida());
                m.setClasificacionConfianza(90);
                matched = true;
                break;
            }
        }
        // E7: fallback
        if (!matched) {
            m.setTipoMovimiento(m.getAmount().compareTo(BigDecimal.ZERO) > 0 ? "DEPOSITO" : "RETIRO");
            m.setClasificacionConfianza(30);
        }
    }

    /**
     * BNK-HU-068 E8/E10: corrección manual de la clasificación. Sube la confianza a
     * 100 y audita el cambio.
     */
    @Transactional
    public FinancialMovement correctClassification(Long movementId, String tipoMovimiento, String cuentaPucSugerida) {
        FinancialMovement m = movementRepository.findById(movementId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado"));
        String antes = m.getTipoMovimiento();
        if (tipoMovimiento != null) m.setTipoMovimiento(tipoMovimiento.trim());
        if (cuentaPucSugerida != null) m.setCuentaPucSugerida(cuentaPucSugerida.trim());
        m.setClasificacionConfianza(100);
        movementRepository.save(m);
        auditPublisher.publishUpdate(AuditModule.BNK, "FinancialMovement", movementId,
                "Clasificación corregida manualmente: " + antes + " -> " + m.getTipoMovimiento(),
                "{tipo=" + antes + "}", "{tipo=" + m.getTipoMovimiento() + ", confianza=100}");
        return m;
    }

    /**
     * BNK-HU-068 (UI): lista los movimientos de la cuenta con sus campos de
     * pre-procesamiento (normalizada, tipo, confianza, cuenta sugerida) para
     * mostrarlos y permitir corrección manual desde el frontend.
     */
    public List<Map<String, Object>> listClassified(Long bankAccountId) {
        return movementRepository.findAllByBankAccountIdOrdered(bankAccountId).stream().map(m -> {
            Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("id", m.getId());
            r.put("fecha", m.getMovementDate());
            r.put("descripcion", m.getDescription());
            r.put("descripcionNormalizada", m.getDescripcionNormalizada());
            r.put("monto", m.getAmount());
            r.put("numeroCheque", m.getNumeroCheque());
            r.put("nitDetectado", m.getNitDetectado());
            r.put("tipoMovimiento", m.getTipoMovimiento());
            r.put("clasificacionConfianza", m.getClasificacionConfianza());
            r.put("cuentaPucSugerida", m.getCuentaPucSugerida());
            r.put("sourceType", m.getSourceType() != null ? m.getSourceType().name() : null);
            return r;
        }).toList();
    }

    // ---- helpers ----

    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toUpperCase().replaceAll("[^A-Z0-9 ./-]", " ").replaceAll("\\s+", " ").trim();
        return n.length() > 500 ? n.substring(0, 500) : n;
    }

    private String firstGroup(Matcher matcher) {
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) return matcher.group(i);
            }
        }
        return null;
    }

    private boolean aplicaAlcance(ReglaClasificacion r, Long bankAccountId, Long bankId) {
        if ("GLOBAL".equals(r.getAlcance())) return true;
        if ("BANCO".equals(r.getAlcance())) return bankId != null && bankId.equals(r.getBancoId());
        if ("CUENTA".equals(r.getAlcance())) return bankAccountId != null && bankAccountId.equals(r.getCuentaBancariaId());
        return false;
    }

    private boolean signoCoincide(String signo, BigDecimal amount) {
        if (signo == null || "CUALQUIERA".equals(signo)) return true;
        if ("DEBITO".equals(signo)) return amount.compareTo(BigDecimal.ZERO) < 0;
        if ("CREDITO".equals(signo)) return amount.compareTo(BigDecimal.ZERO) > 0;
        return true;
    }

    private boolean montoEnRango(ReglaClasificacion r, BigDecimal amount) {
        BigDecimal abs = amount.abs();
        if (r.getMontoMin() != null && abs.compareTo(r.getMontoMin()) < 0) return false;
        if (r.getMontoMax() != null && abs.compareTo(r.getMontoMax()) > 0) return false;
        return true;
    }
}

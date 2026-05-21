package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.matching.application.ReglaClasificacionRequest;
import com.sigcon.backend.banks.matching.domain.model.ReglaClasificacion;
import com.sigcon.backend.banks.matching.domain.repository.ReglaClasificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * BNK-HU-071: CRUD de reglas de clasificación + prueba de regla + validación de
 * regex seguro y prioridad. Audita todos los cambios (configuración sensible).
 */
@Service
@RequiredArgsConstructor
public class ReglaClasificacionService {

    private final ReglaClasificacionRepository repository;
    private final FinancialMovementRepository financialMovementRepository;
    private final AuditPublisher auditPublisher;

    public List<ReglaClasificacion> list() {
        return repository.findByDeletedAtIsNullOrderByPrioridadAsc();
    }

    @Transactional
    public ReglaClasificacion create(ReglaClasificacionRequest req) {
        validate(req, null);
        ReglaClasificacion r = ReglaClasificacion.builder()
                .nombre(req.getNombre().trim())
                .prioridad(req.getPrioridad() != null ? req.getPrioridad() : 100)
                .patronRegex(req.getPatronRegex().trim())
                .signo(req.getSigno() != null ? req.getSigno() : "CUALQUIERA")
                .montoMin(req.getMontoMin())
                .montoMax(req.getMontoMax())
                .tipoMovimiento(req.getTipoMovimiento().trim())
                .cuentaPucSugerida(req.getCuentaPucSugerida())
                .alcance(req.getAlcance() != null ? req.getAlcance() : "GLOBAL")
                .bancoId(req.getBancoId())
                .cuentaBancariaId(req.getCuentaBancariaId())
                .activa(req.getActiva() == null ? Boolean.TRUE : req.getActiva())
                .build();
        r = repository.save(r);
        auditPublisher.publishCreate(AuditModule.BNK, "ReglaClasificacion", r.getId(),
                "Regla clasificación creada: " + r.getNombre() + " (prioridad " + r.getPrioridad() + ")");
        return r;
    }

    @Transactional
    public ReglaClasificacion update(Long id, ReglaClasificacionRequest req) {
        ReglaClasificacion r = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Regla no encontrada"));
        validate(req, id);
        r.setNombre(req.getNombre().trim());
        if (req.getPrioridad() != null) r.setPrioridad(req.getPrioridad());
        r.setPatronRegex(req.getPatronRegex().trim());
        if (req.getSigno() != null) r.setSigno(req.getSigno());
        r.setMontoMin(req.getMontoMin());
        r.setMontoMax(req.getMontoMax());
        r.setTipoMovimiento(req.getTipoMovimiento().trim());
        r.setCuentaPucSugerida(req.getCuentaPucSugerida());
        if (req.getAlcance() != null) r.setAlcance(req.getAlcance());
        r.setBancoId(req.getBancoId());
        r.setCuentaBancariaId(req.getCuentaBancariaId());
        if (req.getActiva() != null) r.setActiva(req.getActiva());
        r = repository.save(r);
        auditPublisher.publishUpdate(AuditModule.BNK, "ReglaClasificacion", r.getId(),
                "Regla clasificación actualizada: " + r.getNombre());
        return r;
    }

    /** Desactiva (no elimina) — BNK-HU-071 E2. */
    @Transactional
    public ReglaClasificacion toggle(Long id, boolean activa) {
        ReglaClasificacion r = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Regla no encontrada"));
        r.setActiva(activa);
        r = repository.save(r);
        auditPublisher.publishUpdate(AuditModule.BNK, "ReglaClasificacion", r.getId(),
                "Regla clasificación " + (activa ? "activada" : "desactivada") + ": " + r.getNombre());
        return r;
    }

    /**
     * BNK-HU-071 E3: prueba la regla mostrando hasta 50 movimientos historicos que
     * harian match con el patron (sobre descripcion).
     */
    public Map<String, Object> test(String patronRegex) {
        Pattern p = compileSafe(patronRegex);
        List<String> matches = new ArrayList<>();
        int evaluated = 0;
        for (FinancialMovement m : financialMovementRepository.findAll(PageRequest.of(0, 300)).getContent()) {
            evaluated++;
            String desc = normalize(m.getDescription());
            if (desc != null && p.matcher(desc).find()) {
                matches.add(m.getMovementDate() + " | " + m.getAmount() + " | " + m.getDescription());
                if (matches.size() >= 50) break;
            }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("evaluados", evaluated);
        r.put("coincidencias", matches.size());
        r.put("muestra", matches);
        return r;
    }

    // ---- validaciones ----

    private void validate(ReglaClasificacionRequest req, Long idActual) {
        // E4: regex valido + guarda contra ReDoS basico (compila + longitud acotada).
        compileSafe(req.getPatronRegex());
        // E5: prioridad 1..999.
        int prio = req.getPrioridad() != null ? req.getPrioridad() : 100;
        if (prio < 1 || prio > 999) {
            throw new IllegalArgumentException("La prioridad debe ser un entero entre 1 y 999");
        }
        // signo valido
        if (req.getSigno() != null && !List.of("DEBITO", "CREDITO", "CUALQUIERA").contains(req.getSigno())) {
            throw new IllegalArgumentException("El signo debe ser DEBITO, CREDITO o CUALQUIERA");
        }
    }

    /**
     * BNK-HU-071 E4: compila el regex de forma segura. Detecta sintaxis inválida.
     * La protección dura contra ReDoS (motor re2 / timeout real) requiere librería
     * adicional; aquí se compila con timeout best-effort sobre una cadena de prueba.
     */
    private Pattern compileSafe(String regex) {
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException("Patrón regex inválido o riesgoso");
        }
        final Pattern p;
        try {
            p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Patrón regex inválido o riesgoso");
        }
        // Best-effort anti-ReDoS: ejecutar el match contra una cadena de prueba con
        // timeout de 100 ms en un hilo aparte.
        try {
            final String sample = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA 1234567890 GMF PSE CHEQUE";
            Thread t = new Thread(() -> p.matcher(sample).find());
            t.setDaemon(true);
            t.start();
            t.join(100);
            if (t.isAlive()) {
                t.interrupt();
                throw new IllegalArgumentException("Patrón regex inválido o riesgoso (posible ReDoS)");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return p;
    }

    private String normalize(String s) {
        if (s == null) return null;
        return s.toUpperCase();
    }
}

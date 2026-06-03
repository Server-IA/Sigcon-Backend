package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.domain.model.AuditRiskRule;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.repository.AuditRiskRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HU-AU-04: Servicio de reglas configurables de clasificacion por riesgo.
 *
 * <p>Funciones:
 * <ul>
 *   <li>{@link #classify}: dada una combinacion (modulo, accion, entidad),
 *       evalua las reglas activas en orden de prioridad y retorna la severidad
 *       que matchea. Si ninguna regla matchea, retorna null para que el caller
 *       use la clasificacion estatica por defecto.</li>
 *   <li>CRUD de reglas para que el admin pueda crear/editar/borrar/activar.</li>
 *   <li>Cache simple en memoria invalidada en cada modificacion.</li>
 * </ul>
 *
 * <p>Las reglas se evaluan asi:
 * <ol>
 *   <li>Si {@code matchModule} es null o coincide con el modulo del evento</li>
 *   <li>Y si {@code matchAction} es null o coincide con la accion</li>
 *   <li>Y si {@code matchEntityType} es null o coincide con el tipo de entidad</li>
 *   <li>Entonces la regla matchea y se aplica su {@code severity}</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskRuleService {

    private final AuditRiskRuleRepository repository;

    /**
     * HU-AU-04 E3 (QA Bloque AJ-AU): publicar en el log de auditoria los cambios
     * sobre las propias reglas de riesgo (crear / modificar / activar / eliminar).
     * Antes el CRUD de reglas NO generaba ningun log — el modulo de auditoria no
     * se auditaba a si mismo.
     */
    private final AuditPublisher auditPublisher;

    /** Cache thread-safe de reglas activas ordenadas por prioridad. */
    private final AtomicReference<List<AuditRiskRule>> cachedRules = new AtomicReference<>();

    /**
     * HU-AU-04 E2: Clasifica un evento aplicando las reglas configurables.
     *
     * @return severidad asignada por la primera regla que matchea, o null si
     *         ninguna regla aplica (el caller debe usar la severidad por defecto).
     */
    public AuditSeverity classify(AuditModule module, AuditAction action, String entityType) {
        for (AuditRiskRule rule : getRules()) {
            if (matches(rule, module, action, entityType)) {
                return rule.getSeverity();
            }
        }
        return null;
    }

    private boolean matches(AuditRiskRule rule, AuditModule module,
                             AuditAction action, String entityType) {
        if (rule.getMatchModule() != null && rule.getMatchModule() != module) return false;
        if (rule.getMatchAction() != null && rule.getMatchAction() != action) return false;
        if (rule.getMatchEntityType() != null
                && !rule.getMatchEntityType().equalsIgnoreCase(entityType)) return false;
        return true;
    }

    /**
     * Obtiene reglas desde cache o BD. AU-RF-03: orden prioridad DESC y, a igual
     * prioridad, created_at DESC (la regla mas reciente gana el desempate).
     */
    private List<AuditRiskRule> getRules() {
        List<AuditRiskRule> cached = cachedRules.get();
        if (cached == null) {
            cached = repository.findByEnabledTrueOrderByPriorityDescCreatedAtDesc();
            cachedRules.set(cached);
        }
        return cached;
    }

    /**
     * AU-RF-03 (QA 2026-06-02): la prioridad es un entero entre 1 y 1000.
     * Antes no se validaba y se podian crear reglas con prioridad 9999 (fuera de
     * las bandas semanticas definidas: 1-99 fondo, 100-199 generales,
     * 200-499 especificas, 500-999 criticas/regulatorias).
     */
    private void validatePriority(AuditRiskRule rule) {
        Integer p = rule.getPriority();
        if (p == null) {
            rule.setPriority(100); // default banda "generales"
            return;
        }
        if (p < 1 || p > 1000) {
            throw new IllegalArgumentException(
                    "La prioridad debe ser un entero entre 1 y 1000. "
                    + "Bandas: 1-99 fondo, 100-199 generales, 200-499 especificas, "
                    + "500-999 criticas/regulatorias.");
        }
    }

    /** Invalida el cache (llamar tras crear/editar/borrar reglas). */
    public void invalidateCache() {
        cachedRules.set(null);
    }

    // ─── CRUD ────────────────────────────────────────────────

    public ResponseEntity<?> list() {
        return ResponseEntity.ok(repository.findAll());
    }

    @Transactional
    public ResponseEntity<?> create(AuditRiskRule rule, String createdBy) {
        validatePriority(rule); // AU-RF-03: rango 1-1000
        rule.setCreatedBy(createdBy);
        rule.setEnabled(rule.getEnabled() == null ? true : rule.getEnabled());
        AuditRiskRule saved = repository.save(rule);
        invalidateCache();
        log.info("HU-AU-04: regla creada id={} name='{}'", saved.getId(), saved.getName());
        auditPublisher.publishCreate(AuditModule.AU, "AuditRiskRule", saved.getId(),
                "Regla de riesgo creada: '" + saved.getName() + "' (severidad "
                        + saved.getSeverity() + ", prioridad " + saved.getPriority() + ")");
        return ResponseEntity.ok(saved);
    }

    @Transactional
    public ResponseEntity<?> update(Long id, AuditRiskRule update) {
        validatePriority(update); // AU-RF-03: rango 1-1000
        AuditRiskRule existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Regla no encontrada"));
        existing.setName(update.getName());
        existing.setDescription(update.getDescription());
        existing.setMatchModule(update.getMatchModule());
        existing.setMatchAction(update.getMatchAction());
        existing.setMatchEntityType(update.getMatchEntityType());
        existing.setSeverity(update.getSeverity());
        existing.setPriority(update.getPriority());
        existing.setEnabled(update.getEnabled());
        AuditRiskRule saved = repository.save(existing);
        invalidateCache();
        auditPublisher.publishUpdate(AuditModule.AU, "AuditRiskRule", saved.getId(),
                "Regla de riesgo modificada: '" + saved.getName() + "' (severidad "
                        + saved.getSeverity() + ", prioridad " + saved.getPriority()
                        + ", activa=" + saved.getEnabled() + ")");
        return ResponseEntity.ok(saved);
    }

    @Transactional
    public ResponseEntity<?> toggle(Long id) {
        AuditRiskRule existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Regla no encontrada"));
        existing.setEnabled(!Boolean.TRUE.equals(existing.getEnabled()));
        repository.save(existing);
        invalidateCache();
        auditPublisher.publishUpdate(AuditModule.AU, "AuditRiskRule", existing.getId(),
                "Regla de riesgo " + (Boolean.TRUE.equals(existing.getEnabled()) ? "activada" : "desactivada")
                        + ": '" + existing.getName() + "'");
        return ResponseEntity.ok(existing);
    }

    @Transactional
    public ResponseEntity<?> delete(Long id) {
        AuditRiskRule existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Regla no encontrada"));
        existing.setDeletedAt(LocalDateTime.now());
        repository.save(existing);
        invalidateCache();
        auditPublisher.publishDelete(AuditModule.AU, "AuditRiskRule", existing.getId(),
                "Regla de riesgo eliminada: '" + existing.getName() + "'");
        return ResponseEntity.ok().build();
    }
}

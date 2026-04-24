package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.domain.events.AuditableActionEvent;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Helper para que cualquier servicio publique eventos de auditoria con una
 * linea de codigo en vez de inyectar ApplicationEventPublisher y construir
 * el evento manualmente.
 *
 * <p>Cobertura HU-AU-01/03: cubre operaciones CRUD de todos los modulos
 * (TER, CFG, ACT, BNK, AP, AR, CG, NOM, AU, PA) sin necesidad de crear una
 * clase de evento dedicada por cada servicio.
 *
 * <p>Ejemplo de uso:
 * <pre>
 *   auditPublisher.publishCreate(AuditModule.BNK, "Bank", bank.getId(),
 *       "Banco creado: " + bank.getName());
 * </pre>
 *
 * <p>Todos los eventos viajan por el listener central
 * {@link AuditEventListener#onAuditableAction} que corre en fase
 * {@code AFTER_COMMIT}, asi que si la transaccion original falla el log
 * tampoco se crea (integridad).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publica un evento CREATE.
     *
     * @param module     modulo origen
     * @param entityType nombre simple de la entidad (ej. "Bank", "Employee")
     * @param entityId   id de la entidad recien creada
     * @param description texto legible para el log
     */
    public void publishCreate(AuditModule module, String entityType,
                              Long entityId, String description) {
        publish(AuditAction.CREATE, module, null, entityType, entityId,
                description, null, null, null);
    }

    /**
     * Publica un evento UPDATE.
     */
    public void publishUpdate(AuditModule module, String entityType,
                              Long entityId, String description) {
        publish(AuditAction.UPDATE, module, null, entityType, entityId,
                description, null, null, null);
    }

    /**
     * Publica un evento UPDATE con snapshot del estado anterior/nuevo (JSON).
     */
    public void publishUpdate(AuditModule module, String entityType, Long entityId,
                              String description, String oldValues, String newValues) {
        publish(AuditAction.UPDATE, module, null, entityType, entityId,
                description, oldValues, newValues, null);
    }

    /**
     * Publica un evento DELETE.
     */
    public void publishDelete(AuditModule module, String entityType,
                              Long entityId, String description) {
        publish(AuditAction.DELETE, module, null, entityType, entityId,
                description, null, null, null);
    }

    /**
     * Publica un evento generico con control total sobre parametros.
     */
    public void publish(AuditAction action, AuditModule module, AuditSeverity severity,
                        String entityType, Long entityId, String description,
                        String oldValues, String newValues, Long journalEntryId) {
        try {
            eventPublisher.publishEvent(AuditableActionEvent.builder()
                    .action(action)
                    .module(module)
                    .severity(severity)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .oldValues(oldValues)
                    .newValues(newValues)
                    .journalEntryId(journalEntryId)
                    .build());
        } catch (Exception e) {
            // La auditoria nunca debe romper el flujo de negocio
            log.error("Error publicando evento de auditoria {} {} {}#{}",
                    action, module, entityType, entityId, e);
        }
    }
}

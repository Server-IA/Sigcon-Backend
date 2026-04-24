package com.sigcon.backend.audit.domain.events;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Evento generico de auditoria que cualquier servicio puede publicar sin crear
 * una clase de evento dedicada por cada operacion.
 *
 * <p>Usar a traves de {@link com.sigcon.backend.audit.domain.service.AuditPublisher}
 * que encapsula los parametros mas comunes (module, action, entityType, entityId,
 * description). Si el servicio necesita algo mas especifico (oldValues/newValues,
 * journalEntryId) instanciar el evento directamente con el builder.
 *
 * <p>Cobertura HU-AU-01/03: permite auditar CRUD de cualquier modulo sin tener
 * que crear 50+ clases {@code *Event} dedicadas.
 */
@Getter
@Builder
@AllArgsConstructor
public class AuditableActionEvent {

    private final AuditAction action;
    private final AuditModule module;
    private final AuditSeverity severity;
    private final String entityType;
    private final Long entityId;
    private final String description;
    private final String oldValues;
    private final String newValues;
    private final Long journalEntryId;
}

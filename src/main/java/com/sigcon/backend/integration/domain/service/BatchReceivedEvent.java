package com.sigcon.backend.integration.domain.service;

import org.springframework.context.ApplicationEvent;

/**
 * Evento publicado cuando un IntegrationBatch se persiste exitosamente.
 *
 * <p>Un listener con {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * escucha este evento y dispara el procesamiento async solo DESPUES de que la
 * transaccion de recepcion se haya comiteado. Esto evita el problema donde el
 * procesador async no encuentra el batch porque la transaccion de recepcion
 * aun no se ha hecho commit.
 */
public class BatchReceivedEvent extends ApplicationEvent {

    private final Long batchId;

    public BatchReceivedEvent(Object source, Long batchId) {
        super(source);
        this.batchId = batchId;
    }

    public Long getBatchId() {
        return batchId;
    }
}

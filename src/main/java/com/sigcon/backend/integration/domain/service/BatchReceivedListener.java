package com.sigcon.backend.integration.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener que dispara el procesamiento async de un batch AAEF DESPUES de que
 * la transaccion de recepcion se haya comiteado en BD.
 *
 * <p>Esto garantiza que el {@link AaefBatchProcessor} encuentre el batch al
 * buscarlo por id, evitando el error {@code "Batch no encontrado"} que ocurre
 * cuando una llamada {@code @Async} se dispara dentro de una transaccion que
 * aun no se ha comiteado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchReceivedListener {

    private final AaefBatchProcessor batchProcessor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBatchReceived(BatchReceivedEvent event) {
        log.info("Transaccion commiteada. Disparando procesamiento async para batch {}",
                event.getBatchId());
        batchProcessor.processAsync(event.getBatchId());
    }
}

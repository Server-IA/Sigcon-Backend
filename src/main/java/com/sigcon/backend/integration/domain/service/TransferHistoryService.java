package com.sigcon.backend.integration.domain.service;

import com.sigcon.backend.integration.application.IntegrationTransferHistoryDTO;
import com.sigcon.backend.integration.domain.model.IntegrationTransferHistory;
import com.sigcon.backend.integration.domain.repository.IntegrationTransferHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HU-INT-RF-15 E4: servicio responsable de registrar y consultar el historial
 * append-only de intentos por transfer.
 *
 * <p>El servicio expone metodos {@code recordSuccess}, {@code recordFailure} y
 * {@code recordRetry} que insertan una entrada cada vez que un transfer cambia
 * de estado. Todos los inserts son en transacciones REQUIRES_NEW para que el
 * historial quede persistido aunque la transaccion principal falle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferHistoryService {

    private final IntegrationTransferHistoryRepository historyRepository;

    /**
     * Registra un intento exitoso de procesamiento.
     *
     * @param transferId        ID del transfer asociado
     * @param attemptNumber     0 para intento inicial, 1+ para retries
     * @param accountingEntryId ID del JE generado
     * @param triggerSource     SYSTEM o MANUAL
     * @param triggeredBy       username o "system"
     * @param userNote          nota del usuario al hacer retry (puede ser null)
     * @param newBatchId        si fue un retry, ID del nuevo batch sintetico
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordSuccess(Long transferId, int attemptNumber,
                              Long accountingEntryId, String triggerSource,
                              String triggeredBy, String userNote, Long newBatchId) {
        IntegrationTransferHistory entry = IntegrationTransferHistory.builder()
                .transferId(transferId)
                .attemptNumber(attemptNumber)
                .resultStatus("SUCCESS")
                .accountingEntryId(accountingEntryId)
                .triggerSource(triggerSource)
                .triggeredBy(triggeredBy)
                .userNote(userNote)
                .newBatchId(newBatchId)
                .occurredAt(LocalDateTime.now())
                .build();
        historyRepository.save(entry);
        log.debug("History: SUCCESS transferId={} attempt={} JE={}",
                transferId, attemptNumber, accountingEntryId);
    }

    /**
     * Registra un intento fallido.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordFailure(Long transferId, int attemptNumber,
                              String errorCode, String errorMessage,
                              String triggerSource, String triggeredBy,
                              String userNote, Long newBatchId) {
        IntegrationTransferHistory entry = IntegrationTransferHistory.builder()
                .transferId(transferId)
                .attemptNumber(attemptNumber)
                .resultStatus("FAILED")
                .errorCode(errorCode)
                .errorMessage(truncate(errorMessage, 1000))
                .triggerSource(triggerSource)
                .triggeredBy(triggeredBy)
                .userNote(userNote)
                .newBatchId(newBatchId)
                .occurredAt(LocalDateTime.now())
                .build();
        historyRepository.save(entry);
        log.debug("History: FAILED transferId={} attempt={} code={}",
                transferId, attemptNumber, errorCode);
    }

    /**
     * Registra el inicio de un retry (status RETRYING). El resultado real
     * se registrara despues con recordSuccess o recordFailure.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordRetryAttempt(Long transferId, int attemptNumber,
                                    String triggeredBy, String userNote, Long newBatchId) {
        IntegrationTransferHistory entry = IntegrationTransferHistory.builder()
                .transferId(transferId)
                .attemptNumber(attemptNumber)
                .resultStatus("RETRYING")
                .triggerSource("MANUAL")
                .triggeredBy(triggeredBy)
                .userNote(userNote)
                .newBatchId(newBatchId)
                .occurredAt(LocalDateTime.now())
                .build();
        historyRepository.save(entry);
        log.info("History: RETRYING transferId={} attempt={} by={}",
                transferId, attemptNumber, triggeredBy);
    }

    /**
     * Lista cronologica del historial de un transfer (mas antiguo primero).
     */
    @Transactional(readOnly = true)
    public List<IntegrationTransferHistoryDTO> getHistory(Long transferId) {
        return historyRepository.findByTransferIdOrderByOccurredAtAsc(transferId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Username del usuario actual para registrar el trigger. Devuelve "system" si
     * no hay autenticacion (flujos automaticos: scheduler, async).
     */
    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null
                || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return "system";
        }
        return auth.getName();
    }

    private IntegrationTransferHistoryDTO toDTO(IntegrationTransferHistory h) {
        return IntegrationTransferHistoryDTO.builder()
                .id(h.getId())
                .transferId(h.getTransferId())
                .attemptNumber(h.getAttemptNumber())
                .resultStatus(h.getResultStatus())
                .errorCode(h.getErrorCode())
                .errorMessage(h.getErrorMessage())
                .accountingEntryId(h.getAccountingEntryId())
                .triggerSource(h.getTriggerSource())
                .triggeredBy(h.getTriggeredBy())
                .userNote(h.getUserNote())
                .newBatchId(h.getNewBatchId())
                .occurredAt(h.getOccurredAt())
                .build();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

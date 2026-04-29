package com.sigcon.backend.integration.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.integration.application.AaefAckDTO;
import com.sigcon.backend.integration.application.AgroFusionAcknowledgmentDTO;
import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.IntegrationTransfer;
import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import com.sigcon.backend.integration.domain.model.enums.TransferStatus;
import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import com.sigcon.backend.integration.domain.repository.IntegrationTransferRepository;
import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HU-INT-RF-07 + HU-INT-RF-13: Cliente HTTP outbound que envia el ACK al callback
 * registrado por AgroFusion.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Construir el ACK JSON conforme a RF-INT-12 (status + processedDocuments +
 *       failedDocuments).</li>
 *   <li>Hacer POST al URL configurado en el parametro {@code AGROFUSION_ACK_CALLBACK_URL}.</li>
 *   <li>Actualizar el status del batch a {@code ACK_SENT} o {@code ACK_FAILED}.</li>
 * </ul>
 *
 * <p>NOTA: el retry con backoff exponencial (HU-INT-RF-13) se implementa en un
 * scheduler separado ({@code AckRetryScheduler}) en un paso posterior de Fase 2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgroFusionAckClient {

    private static final String CALLBACK_URL_PARAM = "AGROFUSION_ACK_CALLBACK_URL";
    private static final String MAX_ATTEMPTS_PARAM = "AGROFUSION_ACK_RETRY_MAX_ATTEMPTS";
    private static final String INITIAL_DELAY_PARAM = "AGROFUSION_ACK_RETRY_INITIAL_DELAY_SECONDS";
    /**
     * Spec AAEF Bloque W: API Key fija para autenticar el POST del ACK al
     * callback de AgroFusion. Se envia como header {@code X-API-Key}, mismo
     * mecanismo que SIGCON usa al recibir lotes (HU-INT-RF-12). Configurable
     * via parametro `AGROFUSION_ACK_API_KEY` por canal seguro con AgroFusion.
     *
     * <p>Si esta vacio o ausente, el ACK se envia sin header de autenticacion
     * (modo legacy para tests sin auth). En produccion debe estar configurada.
     */
    private static final String ACK_API_KEY_PARAM = "AGROFUSION_ACK_API_KEY";
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_INITIAL_DELAY_SECONDS = 60;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final IntegrationBatchRepository batchRepository;
    private final IntegrationTransferRepository transferRepository;
    private final ParameterRepository parameterRepository;
    private final ObjectMapper objectMapper;

    /**
     * Construye y envia el ACK al callback de AgroFusion.
     *
     * @param batchId id del lote ya procesado
     */
    @Async
    public void sendAckAsync(Long batchId) {
        try {
            sendAck(batchId);
        } catch (Exception e) {
            log.error("Error enviando ACK para batch {}", batchId, e);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void sendAck(Long batchId) {
        IntegrationBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Batch no encontrado: " + batchId));

        // Solo enviar si el batch termino de procesarse o esta pendiente de retry
        BatchStatus s = batch.getStatus();
        if (s != BatchStatus.PROCESSED && s != BatchStatus.PARTIAL
                && s != BatchStatus.FAILED && s != BatchStatus.ACK_PENDING) {
            log.warn("Batch {} en estado {}; no se envia ACK todavia", batchId, s);
            return;
        }
        if (s == BatchStatus.ACK_SENT) {
            log.debug("Batch {} ya tiene ACK_SENT; no se reenvia", batchId);
            return;
        }

        // Resolver URL de callback global (query bypasea tenant filter).
        // En multi-tenant AgroFusion expone UN solo callback; no hay uno por empresa.
        String callbackUrl = parameterRepository
                .findGlobalValueByName(CALLBACK_URL_PARAM)
                .orElse(null);
        if (callbackUrl == null || callbackUrl.isBlank()) {
            log.error("AGROFUSION_ACK_CALLBACK_URL no configurado. No se puede enviar ACK del batch {}", batchId);
            batch.setStatus(BatchStatus.ACK_FAILED);
            batch.setErrorMessage("URL de callback no configurada");
            batchRepository.save(batch);
            return;
        }

        // Spec AAEF Bloque W: elegir envelope segun el tipo de batch.
        //   - isUpdate=true -> AgroFusionAcknowledgmentDTO PascalCase (Pull+Diff)
        //   - isUpdate=false -> AaefAckDTO camelCase (lote inicial)
        boolean isUpdate = Boolean.TRUE.equals(batch.getIsUpdate());
        String body;
        try {
            if (isUpdate) {
                AgroFusionAcknowledgmentDTO ack = buildUpdateAck(batch);
                body = objectMapper.writeValueAsString(ack);
            } else {
                AaefAckDTO ack = buildAck(batch);
                body = objectMapper.writeValueAsString(ack);
            }
        } catch (JsonProcessingException e) {
            log.error("Error serializando ACK", e);
            batch.setStatus(BatchStatus.ACK_FAILED);
            batch.setErrorMessage("Error serializando ACK: " + e.getMessage());
            batchRepository.save(batch);
            return;
        }

        // Enviar POST con WebClient
        batch.setStatus(BatchStatus.ACK_PENDING);
        batchRepository.save(batch);

        try {
            RestTemplate restTemplate = buildRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Spec AAEF Bloque W: agregar X-API-Key si esta configurada (mismo
            // mecanismo que SIGCON usa al recibir lotes). Si esta vacio el
            // ACK se envia sin auth (modo legacy para tests).
            String apiKey = parameterRepository.findGlobalValueByName(ACK_API_KEY_PARAM).orElse(null);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("X-API-Key", apiKey.trim());
            }
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    callbackUrl, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                batch.setStatus(BatchStatus.ACK_SENT);
                batch.setAckSentAt(LocalDateTime.now());
                batch.setErrorMessage(null);
                batch.setAckNextRetryAt(null);
                batchRepository.save(batch);
                log.info("ACK enviado exitosamente para batch {} (intento #{})",
                        batch.getId(), batch.getAckRetryCount() + 1);
            } else {
                handleAckFailure(batch, "Callback respondio HTTP " + response.getStatusCode().value());
            }
        } catch (Exception e) {
            handleAckFailure(batch, "Error enviando ACK: " + e.getMessage());
        }
    }

    /** Lee el numero maximo de intentos desde parameters (default 3). Query global (bypass tenant filter). */
    private int readMaxAttempts() {
        return parameterRepository.findGlobalValueByName(MAX_ATTEMPTS_PARAM)
                .map(v -> {
                    try { return Integer.parseInt(v); }
                    catch (Exception e) { return DEFAULT_MAX_ATTEMPTS; }
                }).orElse(DEFAULT_MAX_ATTEMPTS);
    }

    /** Lee el delay inicial del backoff desde parameters (default 60s). Query global. */
    private int readInitialDelaySeconds() {
        return parameterRepository.findGlobalValueByName(INITIAL_DELAY_PARAM)
                .map(v -> {
                    try { return Integer.parseInt(v); }
                    catch (Exception e) { return DEFAULT_INITIAL_DELAY_SECONDS; }
                }).orElse(DEFAULT_INITIAL_DELAY_SECONDS);
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    // ======== Construccion del ACK ========

    private AaefAckDTO buildAck(IntegrationBatch batch) {
        List<IntegrationTransfer> transfers =
                transferRepository.findByBatch_IdAndDeletedAtIsNull(batch.getId());

        AaefAckDTO ack = AaefAckDTO.builder()
                .originalExchangeId(batch.getExchangeId())
                .processedAt(OffsetDateTime.now())
                .status(resolveAckStatus(batch))
                .build();

        for (IntegrationTransfer t : transfers) {
            if (t.getTransferStatus() == TransferStatus.PROCESSED) {
                ack.getProcessedDocuments().add(AaefAckDTO.ProcessedDocument.builder()
                        .documentId(t.getDocumentId())
                        .documentType(t.getDocumentType() != null ? t.getDocumentType().name() : null)
                        .accountingEntryId(t.getAccountingEntryId() != null
                                ? String.valueOf(t.getAccountingEntryId()) : null)
                        .status("PROCESSED")
                        .build());
            } else if (t.getTransferStatus() == TransferStatus.FAILED) {
                ack.getFailedDocuments().add(AaefAckDTO.FailedDocument.builder()
                        .documentId(t.getDocumentId())
                        .errorCode(t.getErrorCode())
                        .errorMessage(t.getErrorMessage())
                        .retryAllowed(t.getRetryAllowed())
                        .build());
            }
        }

        return ack;
    }

    /**
     * Spec AAEF Bloque W: construye el ACK del Pull+Diff con envelope PascalCase
     * {@code AgroFusionAcknowledgment}. Envia el {@code OriginalExchangeId}
     * del envelope (lote padre) y los documentos procesados con sus
     * {@code accountingEntryId} (los nuevos asientos generados por la
     * reversa/correccion).
     */
    private AgroFusionAcknowledgmentDTO buildUpdateAck(IntegrationBatch batch) {
        List<IntegrationTransfer> transfers =
                transferRepository.findByBatch_IdAndDeletedAtIsNull(batch.getId());

        AgroFusionAcknowledgmentDTO.Inner inner = AgroFusionAcknowledgmentDTO.Inner.builder()
                .version("1.0")
                // Si el batch tiene OriginalExchangeId conservado por
                // CancellationService, usarlo. Si no, fallback al exchangeId
                // del propio batch sintetico (mismo significado segun spec).
                .originalExchangeId(batch.getOriginalExchangeId() != null
                        ? batch.getOriginalExchangeId() : batch.getExchangeId())
                .processedAt(LocalDateTime.now())
                .status(resolveAckStatus(batch))
                .build();

        for (IntegrationTransfer t : transfers) {
            if (t.getTransferStatus() == TransferStatus.PROCESSED) {
                String docStatus = (t.getAccountingEntryId() == null) ? "NO_CHANGE" : "PROCESSED";
                inner.getProcessedDocuments().add(AgroFusionAcknowledgmentDTO.ProcessedDocument.builder()
                        .documentId(t.getDocumentId())
                        .documentType(t.getDocumentType() != null ? t.getDocumentType().name() : null)
                        .accountingEntryId(t.getAccountingEntryId() != null
                                ? String.valueOf(t.getAccountingEntryId()) : null)
                        .status(docStatus)
                        .build());
            } else if (t.getTransferStatus() == TransferStatus.FAILED) {
                inner.getFailedDocuments().add(AgroFusionAcknowledgmentDTO.FailedDocument.builder()
                        .documentId(t.getDocumentId())
                        .errorCode(t.getErrorCode())
                        .errorMessage(t.getErrorMessage())
                        .retryAllowed(Boolean.TRUE.equals(t.getRetryAllowed()))
                        .build());
            }
        }

        return AgroFusionAcknowledgmentDTO.builder()
                .agroFusionAcknowledgment(inner)
                .build();
    }

    private String resolveAckStatus(IntegrationBatch batch) {
        BatchStatus s = batch.getStatus();
        switch (s) {
            case PROCESSED: return "ACCEPTED";
            case PARTIAL:   return "PARTIAL";
            case FAILED:    return "REJECTED";
            default:        return "PARTIAL";
        }
    }

    private void handleAckFailure(IntegrationBatch batch, String message) {
        int attempt = batch.getAckRetryCount() + 1;
        batch.setAckRetryCount(attempt);
        batch.setErrorMessage(message);
        int maxAttempts = readMaxAttempts();
        if (attempt >= maxAttempts) {
            batch.setStatus(BatchStatus.ACK_FAILED);
            batch.setAckNextRetryAt(null);
            // HU-INT-RF-13 E3: notificacion al administrador contable.
            // SIGCON no tiene servicio de email configurado en este momento,
            // por lo que la notificacion se materializa como log WARN+ERROR
            // visible en /api/contabilidad/lotes (filtro status=ACK_FAILED).
            // Cuando se configure JavaMailSender, agregar emailService.notifyAdmin(...)
            log.error("=== HU-INT-RF-13 E3: ACK_FAILED definitivo ===");
            log.error("Batch ID: {} | ExchangeId: {} | Intentos: {}/{} | Ultimo error: {}",
                    batch.getId(), batch.getExchangeId(), attempt, maxAttempts, message);
            log.error("ACCION REQUERIDA: contactar a AgroFusion para verificar callback. "
                    + "Visible en /integracion/lotes con filtro status=ACK_FAILED");
        } else {
            batch.setStatus(BatchStatus.ACK_PENDING);
            // Backoff exponencial: 1*delay, 2*delay, 4*delay desde ahora
            int delaySeconds = readInitialDelaySeconds() * (int) Math.pow(2, attempt - 1);
            batch.setAckNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
            log.warn("HU-INT-RF-13: ACK fallo intento {}/{} para batch {}: {}. Proximo retry en {}s",
                    attempt, maxAttempts, batch.getId(), message, delaySeconds);
        }
        batchRepository.save(batch);
    }
}

package com.sigcon.backend.integration.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.integration.application.AaefBatchRequest;
import com.sigcon.backend.integration.application.AaefMetadataDTO;
import com.sigcon.backend.integration.application.AaefSummaryDTO;
import com.sigcon.backend.integration.application.IntegrationBatchDTO;
import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.IntegrationIdempotencyKey;
import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import com.sigcon.backend.integration.domain.repository.IntegrationIdempotencyKeyRepository;
import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * HU-INT-RF-01 + HU-INT-RF-03: Receptor de lotes AAEF con idempotencia.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Validar esquema AAEF (delega en {@link AaefValidatorService}).</li>
 *   <li>Verificar idempotencia por {@code (exchangeId, standardVersion)}.</li>
 *   <li>Persistir el lote en {@code integration_batches} con status RECEIVED.</li>
 *   <li>Registrar la clave de idempotencia.</li>
 * </ul>
 *
 * <p>NO procesa el lote contablemente. Eso es trabajo de Fase 2 ({@code AaefBatchProcessor}).
 *
 * <p>Excepciones especificas lanzadas:
 * <ul>
 *   <li>{@link ValidationException} → HTTP 400</li>
 *   <li>{@link DuplicateBatchException} → HTTP 409</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AaefReceiverService {

    private final AaefValidatorService validator;
    private final IntegrationBatchRepository batchRepository;
    private final IntegrationIdempotencyKeyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyRepository companyRepository;

    /**
     * Recibe, valida, chequea idempotencia y persiste un lote AAEF.
     *
     * @param batch request deserializado
     * @return DTO del lote persistido
     * @throws ValidationException si la validacion de esquema falla
     * @throws DuplicateBatchException si el exchangeId+standardVersion ya existe
     */
    @Transactional
    public IntegrationBatchDTO receive(AaefBatchRequest batch) {
        // 1. Validar esquema
        List<String> errors = validator.validate(batch);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        AaefMetadataDTO meta = batch.getMetadata();
        String exchangeId = meta.getExchangeId();
        String version = meta.getStandardVersion();

        // 1.5. Multi-tenant: resolver empresa destino por NIT del SourceSystem y
        // setear TenantContext para que @PrePersist inyecte el company_id correcto.
        // RF-INT-12: el NIT viene en metadata.SourceSystem.SystemNIT. Si no existe
        // empresa con ese NIT, rechazar con COMPANY_NOT_FOUND (400).
        String destinationNit = (meta.getSourceSystem() != null)
                ? meta.getSourceSystem().getSystemNIT()
                : null;
        if (destinationNit == null || destinationNit.isBlank()) {
            throw new ValidationException(List.of(
                    "metadata.SourceSystem.SystemNIT es obligatorio para identificar la empresa destino"));
        }
        Company company = companyRepository.findByNitAndDeletedAtIsNull(destinationNit)
                .orElseThrow(() -> new CompanyNotFoundException(destinationNit));
        if (company.getStatus() != Company.CompanyStatus.ACTIVE) {
            throw new CompanyNotFoundException(destinationNit + " (empresa en estado "
                    + company.getStatus() + ")");
        }
        // Activar el contexto tenant para toda la transaccion y el procesamiento async.
        // AaefBatchProcessor ya lee TenantContext al arrancar el @Async (via evento).
        TenantContext.setCompanyId(company.getId());
        log.info("AAEF: empresa destino resuelta por NIT={} -> companyId={}, name='{}'",
                destinationNit, company.getId(), company.getBusinessName());

        // 2. Idempotencia
        Optional<IntegrationBatch> existing = batchRepository
                .findByExchangeIdAndStandardVersionAndDeletedAtIsNull(exchangeId, version);
        if (existing.isPresent()) {
            // Actualizar contador de intentos
            idempotencyRepository
                    .findByExchangeIdAndStandardVersion(exchangeId, version)
                    .ifPresent(key -> {
                        key.setAttemptCount(key.getAttemptCount() + 1);
                        key.setLastAttemptAt(LocalDateTime.now());
                        idempotencyRepository.save(key);
                    });
            log.warn("Lote duplicado recibido: exchangeId={}, version={}", exchangeId, version);
            throw new DuplicateBatchException(exchangeId, existing.get().getId());
        }

        // 3. Serializar payload completo como JSON para conservar
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(batch);
        } catch (JsonProcessingException e) {
            log.error("No se pudo serializar el payload AAEF", e);
            throw new IllegalStateException("Error interno al serializar payload AAEF", e);
        }

        // 4. Persistir batch
        AaefSummaryDTO summary = batch.getSummary();
        IntegrationBatch entity = IntegrationBatch.builder()
                .exchangeId(exchangeId)
                .standardVersion(version)
                .sourceSystemId(meta.getSourceSystem() != null ? meta.getSourceSystem().getSystemId() : null)
                .sourceSystemName(meta.getSourceSystem() != null ? meta.getSourceSystem().getSystemName() : null)
                .sourceSystemNit(meta.getSourceSystem() != null ? meta.getSourceSystem().getSystemNIT() : null)
                .environment(meta.getSourceSystem() != null ? meta.getSourceSystem().getEnvironment() : null)
                .generatedBy(meta.getGeneratedBy())
                .periodFrom(meta.getRequestedPeriod() != null ? meta.getRequestedPeriod().getFrom() : null)
                .periodTo(meta.getRequestedPeriod() != null ? meta.getRequestedPeriod().getTo() : null)
                // Bug auto-unboxing: el ternario `Integer : 0` (donde 0 es int)
                // forzaba unboxing de getTotalXxx() ANTES de pasar a safeInt(),
                // produciendo NPE si el campo venia null. Pasar `null` (Integer)
                // como brazo alterno deja a safeInt() resolver -> 0.
                .totalDocuments(safeInt(summary != null ? summary.getTotalDocuments() : null))
                .totalInvoices(safeInt(summary != null ? summary.getTotalInvoices() : null))
                .totalTransactions(safeInt(summary != null ? summary.getTotalTransactions() : null))
                .totalGrossAmount(safeBigDecimal(summary != null ? summary.getTotalGrossAmount() : null))
                .totalTaxes(safeBigDecimal(summary != null ? summary.getTotalTaxes() : null))
                .totalNet(safeBigDecimal(summary != null ? summary.getTotalNet() : null))
                .currency(summary != null ? summary.getCurrency() : null)
                .status(BatchStatus.RECEIVED)
                .payloadJson(payloadJson)
                .build();

        entity = batchRepository.save(entity);

        // 5. Registrar idempotencia
        IntegrationIdempotencyKey key = IntegrationIdempotencyKey.builder()
                .exchangeId(exchangeId)
                .standardVersion(version)
                .batchId(entity.getId())
                .lastAttemptAt(LocalDateTime.now())
                .attemptCount(1)
                .build();
        idempotencyRepository.save(key);

        log.info("Lote AAEF recibido: id={}, exchangeId={}, documentos={}",
                entity.getId(), exchangeId, entity.getTotalDocuments());

        // Fase 2: publicar evento. El BatchReceivedListener (con
        // @TransactionalEventListener AFTER_COMMIT) disparara el procesamiento
        // async DESPUES de que esta transaccion se haya comiteado.
        eventPublisher.publishEvent(new BatchReceivedEvent(this, entity.getId()));

        return toDTO(entity);
    }

    /** Consulta un batch por id (usado en endpoint de estado - HU-INT-RF-09). */
    public Optional<IntegrationBatchDTO> findById(Long id) {
        return batchRepository.findById(id).map(this::toDTO);
    }

    // ---------- Helpers ----------

    private IntegrationBatchDTO toDTO(IntegrationBatch e) {
        return IntegrationBatchDTO.builder()
                .id(e.getId())
                .exchangeId(e.getExchangeId())
                .standardVersion(e.getStandardVersion())
                .sourceSystemId(e.getSourceSystemId())
                .sourceSystemName(e.getSourceSystemName())
                .sourceSystemNit(e.getSourceSystemNit())
                .environment(e.getEnvironment())
                .periodFrom(e.getPeriodFrom())
                .periodTo(e.getPeriodTo())
                .totalDocuments(e.getTotalDocuments())
                .totalInvoices(e.getTotalInvoices())
                .totalTransactions(e.getTotalTransactions())
                .totalGrossAmount(e.getTotalGrossAmount())
                .totalTaxes(e.getTotalTaxes())
                .totalNet(e.getTotalNet())
                .currency(e.getCurrency())
                .status(e.getStatus() != null ? e.getStatus().name() : null)
                .receivedAt(e.getReceivedAt())
                .processedAt(e.getProcessedAt())
                .ackSentAt(e.getAckSentAt())
                .ackRetryCount(e.getAckRetryCount())
                .errorMessage(e.getErrorMessage())
                .build();
    }

    private int safeInt(Integer v) { return v == null ? 0 : v; }

    private BigDecimal safeBigDecimal(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    // ---------- Excepciones especificas ----------

    /** Error de validacion de esquema AAEF. Mapeada a HTTP 400. */
    public static class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = errors;
        }

        public List<String> getErrors() { return errors; }
    }

    /** Lote duplicado por exchangeId+version. Mapeada a HTTP 409. */
    public static class DuplicateBatchException extends RuntimeException {
        private final String exchangeId;
        private final Long existingBatchId;

        public DuplicateBatchException(String exchangeId, Long existingBatchId) {
            super("exchangeId ya procesado: " + exchangeId + " (batch existente id=" + existingBatchId + ")");
            this.exchangeId = exchangeId;
            this.existingBatchId = existingBatchId;
        }

        public String getExchangeId() { return exchangeId; }
        public Long getExistingBatchId() { return existingBatchId; }
    }
}

package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AAEF v1.0 - Envelope del lote (RF-INT-13).
 *
 * <p>Es el payload JSON raiz que AgroFusion envia en
 * {@code POST /api/contabilidad/aaef}. Contiene metadata, summary y dos
 * arrays de documentos: invoices y transactions.
 *
 * <p>El bloque "payroll" del estandar AAEF original era un borrador del grupo
 * de documentacion y fue desestimado del alcance del proyecto. Si llegara
 * en el payload sera ignorado por el procesador.
 *
 * <p>En Fase 1 NO se deserializan los documentos internos (invoices, transactions)
 * en clases tipadas — se mantienen como {@code JsonNode} para poder persistir
 * el payload original sin perdida. El mapeo tipado se hace en Fase 2 con clases
 * dedicadas ({@code AaefInvoiceDTO}, {@code AaefTransactionDTO}).
 *
 * <p>Esta estrategia permite:
 * <ul>
 *   <li>Validar la estructura del envelope sin ser estrictos con el contenido interno.</li>
 *   <li>Persistir el payload JSON original para auditoria (5+ anios).</li>
 *   <li>Permitir reprocesamiento posterior con schema evolution.</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AaefBatchRequest {

    /**
     * Metadata obligatoria: exchangeId, standardVersion, sourceSystem, etc.
     */
    @JsonProperty("metadata")
    @NotNull(message = "El campo 'metadata' es obligatorio")
    @Valid
    private AaefMetadataDTO metadata;

    /**
     * Summary de totales del lote (TotalDocuments, TotalInvoices, etc.).
     */
    @JsonProperty("summary")
    @Valid
    private AaefSummaryDTO summary;

    /**
     * Array de facturas. En Fase 1 se mantiene como JsonNode; en Fase 2
     * se desarrolla el DTO tipado {@code AaefInvoiceDTO}.
     */
    @JsonProperty("invoices")
    private List<JsonNode> invoices;

    /**
     * Array de transacciones (PAY, ADV, REF, ADJ).
     */
    @JsonProperty("transactions")
    private List<JsonNode> transactions;
}

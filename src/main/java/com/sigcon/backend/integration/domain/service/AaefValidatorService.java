package com.sigcon.backend.integration.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sigcon.backend.integration.application.AaefBatchRequest;
import com.sigcon.backend.integration.application.AaefMetadataDTO;
import com.sigcon.backend.integration.application.AaefSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HU-INT-RF-02: Servicio de validacion estructural del lote AAEF.
 *
 * <p>Valida que el lote cumpla las reglas minimas del estandar AAEF v1.0 antes
 * de persistirlo en {@code integration_batches}. No valida el contenido interno
 * de invoices/transactions (eso se hace en el procesador async en Fase 2).
 *
 * <p>Reglas cubiertas en Fase 1:
 * <ul>
 *   <li>Metadata obligatoria (exchangeId, standardVersion).</li>
 *   <li>StandardVersion soportada.</li>
 *   <li>Summary coincide con conteo real de documentos en los arrays.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AaefValidatorService {

    /**
     * Versiones del estandar AAEF que SIGCON soporta actualmente.
     *
     * <p>QA Bloque AT (HU-INT-RF-01, 2026-05-13): AgroFusion en produccion
     * emite lotes con {@code StandardVersion=2.0} (estandar AAEF v2 con
     * extensiones para Pull+Diff y campos opcionales). El contrato base de
     * payload (metadata, summary, invoices, transactions) sigue siendo
     * compatible — los campos extra simplemente se ignoran por Jackson.
     * Aceptamos ambas versiones.
     */
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("1.0", "2.0");

    /**
     * Valida la estructura del lote AAEF.
     *
     * @param batch lote recibido deserializado
     * @return lista de errores encontrados (vacia = valido)
     */
    public List<String> validate(AaefBatchRequest batch) {
        List<String> errors = new ArrayList<>();

        if (batch == null) {
            errors.add("El lote es nulo");
            return errors;
        }

        // ----- Metadata obligatoria -----
        AaefMetadataDTO meta = batch.getMetadata();
        if (meta == null) {
            errors.add("Campo obligatorio faltante: metadata");
            return errors;
        }

        if (isBlank(meta.getExchangeId())) {
            errors.add("Campo obligatorio faltante: metadata.exchangeId");
        }

        if (isBlank(meta.getStandardVersion())) {
            errors.add("Campo obligatorio faltante: metadata.standardVersion");
        } else if (!SUPPORTED_VERSIONS.contains(meta.getStandardVersion())) {
            errors.add("StandardVersion no soportada. Versiones validas: " + SUPPORTED_VERSIONS);
        }

        // ----- Summary: si existe, validar que coincida con conteo real -----
        // Nota: el bloque "payroll" del estandar AAEF original era un borrador
        // desestimado del alcance, por lo que se ignora aqui (no se cuenta ni
        // se valida contra summary.totalPayroll).
        AaefSummaryDTO summary = batch.getSummary();
        int realInvoices = count(batch.getInvoices());
        int realTransactions = count(batch.getTransactions());
        int realTotal = realInvoices + realTransactions;

        if (summary != null) {
            if (summary.getTotalDocuments() != null
                    && summary.getTotalDocuments() != realTotal) {
                errors.add("TotalDocuments del summary (" + summary.getTotalDocuments()
                        + ") no coincide con la suma real de documentos (" + realTotal + ")");
            }
            if (summary.getTotalInvoices() != null
                    && summary.getTotalInvoices() != realInvoices) {
                errors.add("TotalInvoices del summary (" + summary.getTotalInvoices()
                        + ") no coincide con facturas reales (" + realInvoices + ")");
            }
            if (summary.getTotalTransactions() != null
                    && summary.getTotalTransactions() != realTransactions) {
                errors.add("TotalTransactions del summary (" + summary.getTotalTransactions()
                        + ") no coincide con transacciones reales (" + realTransactions + ")");
            }
        }

        if (!errors.isEmpty()) {
            log.warn("Validacion AAEF fallida para lote {}: {} error(es)",
                    meta.getExchangeId(), errors.size());
        }

        return errors;
    }

    /**
     * QA Bloque BJ (HU-INT-RF-02 E5 + HU-INT-RF-03 E4, 2026-05-18): genera
     * warnings INFORMATIVOS que NO bloquean el procesamiento.
     *
     * <p>Casos detectados:
     * <ul>
     *   <li>HU-INT-RF-02 E5: factura sin campo {@code UpdatedAt}. La HU pide
     *       procesar normalmente pero advertir al cliente.</li>
     *   <li>HU-INT-RF-03 E4: {@code DocumentId} duplicado dentro del mismo lote.
     *       La HU pide procesar una sola vez e informar que el duplicado fue
     *       detectado y omitido. El procesador async usa la misma deteccion para
     *       skip de duplicados.</li>
     * </ul>
     *
     * @param batch lote a inspeccionar (asume validacion estructural previa OK)
     * @return lista de strings con advertencias legibles (vacia si no hay warnings)
     */
    public List<String> collectWarnings(AaefBatchRequest batch) {
        List<String> warnings = new ArrayList<>();
        if (batch == null) return warnings;

        // RF-02 E5: invoices sin UpdatedAt
        // RF-03 E4: DocumentId duplicado intra-batch
        Set<String> seenInvoiceIds = new HashSet<>();
        if (batch.getInvoices() != null) {
            for (int i = 0; i < batch.getInvoices().size(); i++) {
                JsonNode node = batch.getInvoices().get(i);
                if (node == null) continue;
                String docId = readText(node, "Header", "DocumentId");
                if (docId != null && !seenInvoiceIds.add(docId)) {
                    warnings.add("Factura con DocumentId='" + docId + "' aparece duplicada "
                            + "dentro del lote. Solo se procesara la primera ocurrencia; "
                            + "el duplicado se omitira (HU-INT-RF-03 E4).");
                }
                if (readText(node, "Header", "UpdatedAt") == null) {
                    String ref = docId != null ? docId : ("[indice " + i + "]");
                    warnings.add("Factura " + ref + " no tiene campo Header.UpdatedAt. "
                            + "Se procesa de todos modos pero AgroFusion deberia incluir "
                            + "la fecha de ultima actualizacion para trazabilidad (HU-INT-RF-02 E5).");
                }
            }
        }

        // RF-03 E4 aplicado tambien a transactions
        Set<String> seenTxIds = new HashSet<>();
        if (batch.getTransactions() != null) {
            for (int i = 0; i < batch.getTransactions().size(); i++) {
                JsonNode node = batch.getTransactions().get(i);
                if (node == null) continue;
                JsonNode docIdNode = node.get("DocumentId");
                String docId = docIdNode != null && !docIdNode.isNull() ? docIdNode.asText() : null;
                if (docId != null && !seenTxIds.add(docId)) {
                    warnings.add("Transaccion con DocumentId='" + docId + "' aparece duplicada "
                            + "dentro del lote. Solo se procesara la primera ocurrencia; "
                            + "el duplicado se omitira (HU-INT-RF-03 E4).");
                }
            }
        }

        return warnings;
    }

    /** Helper para leer un campo anidado del JsonNode. Retorna null si no existe. */
    private String readText(JsonNode root, String... path) {
        JsonNode current = root;
        for (String p : path) {
            if (current == null) return null;
            current = current.get(p);
        }
        return current == null || current.isNull() ? null : current.asText();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private int count(List<JsonNode> list) {
        return list == null ? 0 : list.size();
    }
}

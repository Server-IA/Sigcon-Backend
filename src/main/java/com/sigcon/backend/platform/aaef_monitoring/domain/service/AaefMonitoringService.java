package com.sigcon.backend.platform.aaef_monitoring.domain.service;

import com.sigcon.backend.integration.domain.model.IntegrationBatch;
import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import com.sigcon.backend.integration.domain.repository.IntegrationBatchRepository;
import com.sigcon.backend.platform.audit.domain.service.PlatformAuditService;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HU-PA-PLAT-05: Monitoreo AAEF cross-tenant para PLATFORM_ADMIN.
 *
 * <p>Acceso solo por PLATFORM_ADMIN. Las consultas se ejecutan con
 * {@link TenantContext#isPlatformAdmin()} = true para que el
 * {@code TenantFilterAspect} bypasee el filter y vea lotes de TODAS las
 * empresas.
 *
 * <p>NO expone contenido de documentos (HU-PA-PLAT-05 E6). Solo metadatos
 * estructurales: tipo de documento, estado, codigo de error, latencia.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaefMonitoringService {

    private final IntegrationBatchRepository batchRepository;
    private final CompanyRepository companyRepository;

    @Autowired(required = false)
    private com.sigcon.backend.integration.domain.service.AgroFusionAckClient ackClient;
    @Autowired(required = false)
    private PlatformAuditService platformAuditService;

    /**
     * HU-PA-PLAT-05 E1+E2: ventana de 24/48/72/168h con conteos por estado y
     * filtro opcional por empresa.
     */
    public Map<String, Object> getOverview(int hoursWindow, Long companyIdFilter) {
        if (hoursWindow <= 0) hoursWindow = 24;
        LocalDateTime since = LocalDateTime.now().minusHours(hoursWindow);

        // Bypass tenant filter: ya estamos en TenantContext de PLATFORM_ADMIN.
        List<IntegrationBatch> all = batchRepository.findAll();
        List<IntegrationBatch> inWindow = all.stream()
                .filter(b -> b.getDeletedAt() == null)
                .filter(b -> b.getReceivedAt() != null && !b.getReceivedAt().isBefore(since))
                .filter(b -> companyIdFilter == null
                        || (b.getCompanyId() != null && b.getCompanyId().equals(companyIdFilter)))
                .collect(Collectors.toList());

        Map<String, Long> byStatus = new HashMap<>();
        for (BatchStatus s : BatchStatus.values()) byStatus.put(s.name(), 0L);
        for (IntegrationBatch b : inWindow) {
            String key = b.getStatus() != null ? b.getStatus().name() : "UNKNOWN";
            byStatus.merge(key, 1L, Long::sum);
        }

        // Companies en la ventana (cross-tenant)
        Map<Long, Long> byCompany = inWindow.stream()
                .filter(b -> b.getCompanyId() != null)
                .collect(Collectors.groupingBy(IntegrationBatch::getCompanyId, Collectors.counting()));

        Map<Long, String> companyNames = new HashMap<>();
        companyRepository.findAll().forEach(c -> companyNames.put(c.getId(), c.getBusinessName()));

        List<Map<String, Object>> companyBuckets = byCompany.entrySet().stream()
                .map(e -> Map.of(
                        "companyId", (Object) e.getKey(),
                        "companyName", (Object) companyNames.getOrDefault(e.getKey(), "?"),
                        "count", (Object) e.getValue()))
                .sorted((a, b) -> Long.compare(((Long) b.get("count")), ((Long) a.get("count"))))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowHours", hoursWindow);
        result.put("since", since);
        result.put("totalBatches", (long) inWindow.size());
        result.put("byStatus", byStatus);
        result.put("byCompany", companyBuckets);
        return result;
    }

    /**
     * HU-PA-PLAT-05 E1+E2: lista paginada (limit/offset). Devuelve metadata
     * estructural sin payload_json (E6).
     */
    public List<Map<String, Object>> listBatches(int hoursWindow, Long companyIdFilter,
                                                  String statusFilter, int limit, int offset) {
        if (hoursWindow <= 0) hoursWindow = 168; // 7 dias por defecto en listado
        if (limit <= 0 || limit > 500) limit = 50;
        if (offset < 0) offset = 0;
        LocalDateTime since = LocalDateTime.now().minusHours(hoursWindow);

        Map<Long, String> companyNames = new HashMap<>();
        companyRepository.findAll().forEach(c -> companyNames.put(c.getId(), c.getBusinessName()));

        List<IntegrationBatch> all = batchRepository.findAll();
        return all.stream()
                .filter(b -> b.getDeletedAt() == null)
                .filter(b -> b.getReceivedAt() != null && !b.getReceivedAt().isBefore(since))
                .filter(b -> companyIdFilter == null
                        || (b.getCompanyId() != null && b.getCompanyId().equals(companyIdFilter)))
                .filter(b -> {
                    if (statusFilter == null || statusFilter.isBlank()) return true;
                    return b.getStatus() != null && b.getStatus().name().equalsIgnoreCase(statusFilter);
                })
                .sorted(Comparator.comparing(IntegrationBatch::getReceivedAt).reversed())
                .skip(offset).limit(limit)
                .map(b -> batchToMeta(b, companyNames))
                .collect(Collectors.toList());
    }

    private Map<String, Object> batchToMeta(IntegrationBatch b, Map<Long, String> companyNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("exchangeId", b.getExchangeId());
        m.put("companyId", b.getCompanyId());
        m.put("companyName", b.getCompanyId() != null ? companyNames.get(b.getCompanyId()) : null);
        m.put("status", b.getStatus() != null ? b.getStatus().name() : null);
        m.put("receivedAt", b.getReceivedAt());
        m.put("processedAt", b.getProcessedAt());
        m.put("ackSentAt", b.getAckSentAt());
        m.put("ackRetryCount", b.getAckRetryCount());
        m.put("totalDocuments", b.getTotalDocuments());
        m.put("totalInvoices", b.getTotalInvoices());
        m.put("totalTransactions", b.getTotalTransactions());
        // HU-PA-PLAT-05 E6: NO incluimos payload_json ni contenido de documentos
        if (b.getReceivedAt() != null && b.getProcessedAt() != null) {
            m.put("processingMs", Duration.between(b.getReceivedAt(), b.getProcessedAt()).toMillis());
        }
        if (b.getReceivedAt() != null && b.getAckSentAt() != null) {
            m.put("totalLatencyMs", Duration.between(b.getReceivedAt(), b.getAckSentAt()).toMillis());
        }
        return m;
    }

    /**
     * HU-PA-PLAT-05 E3: latencia promedio por empresa en ultimos 7 dias.
     */
    public List<Map<String, Object>> latencyByCompany() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Map<Long, String> companyNames = new HashMap<>();
        companyRepository.findAll().forEach(c -> companyNames.put(c.getId(), c.getBusinessName()));

        List<IntegrationBatch> all = batchRepository.findAll();
        Map<Long, List<Long>> latencyByCompany = new HashMap<>();
        for (IntegrationBatch b : all) {
            if (b.getDeletedAt() != null) continue;
            if (b.getReceivedAt() == null || b.getReceivedAt().isBefore(since)) continue;
            if (b.getProcessedAt() == null || b.getCompanyId() == null) continue;
            long ms = Duration.between(b.getReceivedAt(), b.getProcessedAt()).toMillis();
            latencyByCompany.computeIfAbsent(b.getCompanyId(), k -> new ArrayList<>()).add(ms);
        }

        return latencyByCompany.entrySet().stream()
                .map(e -> {
                    List<Long> values = e.getValue();
                    double avg = values.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    long min = values.stream().mapToLong(Long::longValue).min().orElse(0L);
                    long max = values.stream().mapToLong(Long::longValue).max().orElse(0L);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("companyId", e.getKey());
                    m.put("companyName", companyNames.getOrDefault(e.getKey(), "?"));
                    m.put("samples", values.size());
                    m.put("avgMs", Math.round(avg));
                    m.put("minMs", min);
                    m.put("maxMs", max);
                    return m;
                })
                .sorted((a, b) -> Long.compare(((Number) b.get("avgMs")).longValue(),
                        ((Number) a.get("avgMs")).longValue()))
                .collect(Collectors.toList());
    }

    /**
     * HU-PA-PLAT-05 E4: cuenta lotes en ACK_PENDING con mas de N horas.
     */
    public Map<String, Object> retryAlerts(long thresholdHours) {
        if (thresholdHours <= 0) thresholdHours = 1;
        LocalDateTime cutoff = LocalDateTime.now().minusHours(thresholdHours);
        List<IntegrationBatch> pending = batchRepository
                .findByStatusInAndDeletedAtIsNull(List.of(BatchStatus.ACK_PENDING));
        long count = pending.stream()
                .filter(b -> b.getReceivedAt() != null && b.getReceivedAt().isBefore(cutoff))
                .count();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("thresholdHours", thresholdHours);
        r.put("pendingCount", count);
        r.put("warning", count > 0
                ? ("Hay " + count + " lotes con confirmaciones fallidas a AgroFusion con más de "
                  + thresholdHours + " hora(s) pendientes")
                : null);
        return r;
    }

    /**
     * HU-PA-PLAT-05 E5: dispara reintento manual del ACK de un lote especifico.
     */
    @Transactional
    public Map<String, Object> retryAck(Long batchId) {
        IntegrationBatch b = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));
        if (b.getStatus() != BatchStatus.ACK_PENDING && b.getStatus() != BatchStatus.ACK_FAILED) {
            throw new IllegalStateException(
                    "El lote esta en estado " + b.getStatus()
                  + ". Solo se puede reintentar desde ACK_PENDING o ACK_FAILED");
        }

        boolean ok = false;
        String message;
        try {
            if (ackClient != null) {
                ackClient.sendAck(b.getId());
                IntegrationBatch reloaded = batchRepository.findById(b.getId()).orElse(b);
                ok = reloaded.getStatus() == BatchStatus.ACK_SENT;
                message = ok ? "ACK reenviado correctamente"
                        : "Reintento ejecutado, lote queda en " + reloaded.getStatus();
            } else {
                message = "AgroFusionAckClient no disponible en este perfil";
            }
        } catch (RuntimeException ex) {
            message = "Reintento fallo: " + ex.getMessage();
        }

        if (platformAuditService != null) {
            platformAuditService.log("AAEF_BATCH_RETRIED", "IntegrationBatch",
                    String.valueOf(b.getId()), b.getExchangeId(),
                    PlatformAuditService.payload("ok", ok, "message", message),
                    null);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", ok);
        r.put("message", message);
        r.put("batchId", b.getId());
        return r;
    }
}

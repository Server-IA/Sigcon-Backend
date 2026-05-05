package com.sigcon.backend.platform.dashboard.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HU-PA-PLAT-06 E3: registro en memoria de metricas de requests HTTP para
 * exponer en el dashboard de plataforma.
 *
 * <p>Captura por cada request:
 * <ul>
 *   <li>Endpoint normalizado (method + URI con placeholders).</li>
 *   <li>Duracion en milisegundos.</li>
 *   <li>Status HTTP.</li>
 *   <li>Timestamp.</li>
 * </ul>
 *
 * <p>Mantiene una ventana deslizante de la ultima 1 hora. Calcula:
 * <ul>
 *   <li>Requests por minuto (rate global).</li>
 *   <li>Latencia p50 / p95 / p99 global y por endpoint.</li>
 *   <li>Top-10 endpoints con mas errores 5xx en la ultima hora.</li>
 * </ul>
 *
 * <p>Implementacion: en memoria, sin dependencias externas (Prometheus / Micrometer).
 * Cada entrada {@code RequestRecord} ocupa ~80 bytes; con 100 req/min tenemos
 * ~6000 records/hora = ~480 KB. Aceptable para SIGCON.
 *
 * <p>Para uso en produccion con alto volumen, migrar a Micrometer + Prometheus.
 */
@Component
@Slf4j
public class RequestMetricsRegistry {

    /** Ventana de 1 hora */
    private static final long WINDOW_MS = 60L * 60L * 1000L;

    /** Tope de records guardados (proteccion DoS / memory leak). */
    private static final int MAX_RECORDS = 50_000;

    /** Records globales en orden de llegada. */
    private final ConcurrentLinkedDeque<RequestRecord> records = new ConcurrentLinkedDeque<>();

    private final AtomicLong totalSeen = new AtomicLong();

    public void record(String endpoint, long durationMs, int status) {
        long now = System.currentTimeMillis();
        records.add(new RequestRecord(endpoint, durationMs, status, now));
        totalSeen.incrementAndGet();

        // Pruning: cada 100 records limpiamos los viejos. Evita crecer sin limite.
        if (totalSeen.get() % 100 == 0) {
            prune(now);
        }
        if (records.size() > MAX_RECORDS) {
            // Hard cap defensivo
            for (int i = 0; i < 5000 && records.size() > MAX_RECORDS - 5000; i++) {
                records.pollFirst();
            }
        }
    }

    private void prune(long nowMs) {
        long cutoff = nowMs - WINDOW_MS;
        RequestRecord head;
        while ((head = records.peekFirst()) != null && head.timestampMs < cutoff) {
            records.pollFirst();
        }
    }

    /**
     * Snapshot de metricas en la ventana actual.
     */
    public Snapshot snapshot() {
        long now = System.currentTimeMillis();
        prune(now);

        List<RequestRecord> all = new ArrayList<>(records);
        if (all.isEmpty()) {
            return Snapshot.empty();
        }

        long oldestMs = all.get(0).timestampMs;
        long windowDurationMs = Math.max(now - oldestMs, 1L);
        double windowMinutes = windowDurationMs / 60_000.0;

        // Rate global req/min
        double reqPerMin = all.size() / windowMinutes;

        // Latencias globales
        long[] durations = all.stream().mapToLong(r -> r.durationMs).sorted().toArray();
        long p50 = percentile(durations, 50);
        long p95 = percentile(durations, 95);
        long p99 = percentile(durations, 99);

        // Errores 5xx top-10 por endpoint
        Map<String, Long> errorsByEndpoint = new HashMap<>();
        for (RequestRecord r : all) {
            if (r.status >= 500) {
                errorsByEndpoint.merge(r.endpoint, 1L, Long::sum);
            }
        }
        List<EndpointErrors> topErrors = errorsByEndpoint.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new EndpointErrors(e.getKey(), e.getValue()))
                .toList();

        // p95 por endpoint (top-10 mas lentos)
        Map<String, List<Long>> durationsByEndpoint = new HashMap<>();
        for (RequestRecord r : all) {
            durationsByEndpoint.computeIfAbsent(r.endpoint, k -> new ArrayList<>()).add(r.durationMs);
        }
        List<EndpointLatency> topLatency = durationsByEndpoint.entrySet().stream()
                .map(e -> {
                    long[] arr = e.getValue().stream().mapToLong(Long::longValue).sorted().toArray();
                    return new EndpointLatency(e.getKey(), e.getValue().size(), percentile(arr, 95));
                })
                .sorted((a, b) -> Long.compare(b.p95Ms, a.p95Ms))
                .limit(10)
                .toList();

        long total5xx = errorsByEndpoint.values().stream().mapToLong(Long::longValue).sum();
        long total4xx = all.stream().filter(r -> r.status >= 400 && r.status < 500).count();

        return new Snapshot(
                all.size(),
                Math.round(reqPerMin * 100.0) / 100.0,
                p50, p95, p99,
                total4xx, total5xx,
                topErrors,
                topLatency
        );
    }

    private static long percentile(long[] sorted, int p) {
        if (sorted.length == 0) return 0L;
        int idx = (int) Math.ceil((p / 100.0) * sorted.length) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.length) idx = sorted.length - 1;
        return sorted[idx];
    }

    /** Solo para tests / debug. */
    public void clear() {
        records.clear();
        totalSeen.set(0);
    }

    public int size() { return records.size(); }

    // ----------------------- Inner records -----------------------

    public static final class RequestRecord {
        public final String endpoint;
        public final long durationMs;
        public final int status;
        public final long timestampMs;

        public RequestRecord(String endpoint, long durationMs, int status, long timestampMs) {
            this.endpoint = endpoint;
            this.durationMs = durationMs;
            this.status = status;
            this.timestampMs = timestampMs;
        }
    }

    public record EndpointErrors(String endpoint, long count5xx) {}

    public record EndpointLatency(String endpoint, long sampleSize, long p95Ms) {}

    public record Snapshot(
            long totalRequests,
            double requestsPerMinute,
            long p50Ms,
            long p95Ms,
            long p99Ms,
            long errors4xx,
            long errors5xx,
            List<EndpointErrors> top10ErrorsByEndpoint,
            List<EndpointLatency> top10SlowestEndpoints
    ) {
        public static Snapshot empty() {
            return new Snapshot(0, 0.0, 0, 0, 0, 0, 0, List.of(), List.of());
        }
    }
}

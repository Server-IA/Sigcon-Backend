package com.sigcon.backend.audit.domain.service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QA Auditoria (2026-06-02): rate limit de exportaciones de logs.
 *
 * <p>El RF exige que la exportacion no sea ilimitada: tras N exportaciones en una
 * ventana de tiempo, el usuario debe esperar. Por defecto: maximo 20 exportaciones
 * por ventana deslizante de 30 segundos por usuario. Si excede, se le indica
 * cuantos segundos esperar (HTTP 429), nunca de forma silenciosa.
 *
 * <p>Implementacion en memoria por usuario (single-node, consistente con el
 * despliegue actual). Para multi-nodo se requeriria un store compartido (Redis).
 */
@Component
public class AuditExportRateLimiter {

    private static final int MAX_PER_WINDOW = 20;
    private static final long WINDOW_MS = 30_000L;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public int maxPerWindow() { return MAX_PER_WINDOW; }
    public long windowSeconds() { return WINDOW_MS / 1000; }

    /**
     * Registra un intento de exportacion y decide si se permite.
     *
     * @param user  identificador del usuario (email)
     * @param nowMs instante actual en millis
     * @return 0 si se permite (y se registra el hit); o los segundos a esperar si
     *         se alcanzo el limite (no se registra el hit).
     */
    public synchronized long checkAndRecord(String user, long nowMs) {
        String key = user == null ? "anon" : user;
        Deque<Long> dq = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        // purgar hits fuera de la ventana
        while (!dq.isEmpty() && nowMs - dq.peekFirst() > WINDOW_MS) {
            dq.pollFirst();
        }
        if (dq.size() >= MAX_PER_WINDOW) {
            long oldest = dq.peekFirst();
            long waitMs = WINDOW_MS - (nowMs - oldest);
            return Math.max(1, (waitMs + 999) / 1000); // redondea hacia arriba a segundos
        }
        dq.addLast(nowMs);
        return 0;
    }
}

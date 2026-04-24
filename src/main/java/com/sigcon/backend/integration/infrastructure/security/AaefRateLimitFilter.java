package com.sigcon.backend.integration.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RF-INT-12 R12 + HU-INT-RF-13 (rate limit): bloquea con HTTP 429 cuando la
 * misma API Key (o IP si no hay key) supera {@value #MAX_REQUESTS_PER_HOUR}
 * peticiones en {@value #WINDOW_SECONDS} segundos sobre el endpoint
 * {@code POST /api/contabilidad/aaef}.
 *
 * <p>Implementacion: sliding window en memoria por clave (deque de timestamps).
 * Suficiente para single-instance. Si en el futuro se escala horizontalmente,
 * migrar a Redis/Bucket4j.
 *
 * <p>Devuelve mensaje exacto:
 * <em>"Rate limit excedido: maximo 10 lotes por hora. Intente nuevamente en X minutos."</em>
 */
@Slf4j
@Component
public class AaefRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_HOUR = 10;
    private static final long WINDOW_SECONDS = 3600;
    private static final String AAEF_PATH_SUFFIX = "/api/contabilidad/aaef";

    /** Map clave -> ventana deslizante de timestamps (last 1h). */
    private final Map<String, Deque<Instant>> windowByKey = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return true;
        // Solo aplicar al POST /aaef (recibo de lotes); el GET de transferencias y health no rate-limit
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        return !(path.endsWith(AAEF_PATH_SUFFIX) || path.endsWith(AAEF_PATH_SUFFIX + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = resolveKey(request);
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        Deque<Instant> dq = windowByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (dq) {
            // Purgar timestamps fuera de ventana
            while (!dq.isEmpty() && dq.peekFirst().isBefore(windowStart)) {
                dq.pollFirst();
            }
            if (dq.size() >= MAX_REQUESTS_PER_HOUR) {
                long waitSeconds = WINDOW_SECONDS - Duration.between(dq.peekFirst(), now).toSeconds();
                long waitMinutes = Math.max(1, waitSeconds / 60);
                log.warn("AAEF rate limit excedido para key={}, esperar {} min", key, waitMinutes);
                response.setStatus(429); // TOO_MANY_REQUESTS
                response.setHeader("Retry-After", String.valueOf(waitSeconds));
                response.setContentType("application/json");
                response.getWriter().write(String.format(
                    "{\"success\":false,\"code\":429,\"errorCode\":\"RATE_LIMIT_EXCEEDED\","
                  + "\"message\":\"Rate limit excedido: maximo %d lotes por hora. Intente nuevamente en %d minutos.\"}",
                    MAX_REQUESTS_PER_HOUR, waitMinutes));
                return;
            }
            dq.addLast(now);
        }
        chain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest req) {
        String apiKey = req.getHeader(ApiKeyFilter.API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        String fwd = req.getHeader("X-Forwarded-For");
        String ip = (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim() : req.getRemoteAddr();
        return "ip:" + ip;
    }
}

package com.sigcon.backend.integration.infrastructure.security;

import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RF-INT-12 R12 + HU-INT-RF-13 (rate limit): bloquea con HTTP 429 cuando la
 * misma API Key (o IP si no hay key) supera el cupo de peticiones por hora
 * sobre el endpoint {@code POST /api/contabilidad/aaef}.
 *
 * <p>Cupos diferenciados por tier de la API Key entrante:
 * <ul>
 *   <li><b>PRODUCTION</b> (AGROFUSION_API_KEY): {@value #LIMIT_PRODUCTION} lotes/hora.
 *       Es la key que usa AgroFusion en operacion real.</li>
 *   <li><b>TEST</b> (AGROFUSION_API_KEY_TEST): {@value #LIMIT_TEST} lotes/hora.
 *       Para QA / integradores corriendo suites end-to-end sin esperar
 *       ventanas de 1 hora entre baterias.</li>
 *   <li><b>UNKNOWN</b> (sin key valida o IP): se aplica el limite PRODUCTION
 *       como medida defensiva. ApiKeyFilter va a rechazar con 401 de todos
 *       modos despues, asi que este path no debe ocurrir en flujo normal.</li>
 * </ul>
 *
 * <p>Implementacion: sliding window en memoria por clave (deque de timestamps).
 * Suficiente para single-instance. Si en el futuro se escala horizontalmente,
 * migrar a Redis/Bucket4j.
 *
 * <p>Devuelve mensaje exacto:
 * <em>"Rate limit excedido: maximo N lotes por hora. Intente nuevamente en X minutos."</em>
 *
 * <p>Nota: este filter corre ANTES de ApiKeyFilter en la cadena (ver
 * SecurityConfig: addFilterBefore en orden rate -> payload -> jwt -> apiKey).
 * Por eso no podemos leer el atributo {@code aaef.key.tier} que stampa
 * ApiKeyFilter; tenemos que resolver el tier por nuestra cuenta consultando
 * ambas keys via ParameterRepository.findGlobalValueByName.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AaefRateLimitFilter extends OncePerRequestFilter {

    /** Limite por hora cuando la key entrante es PRODUCTION. */
    public static final int LIMIT_PRODUCTION = 10;

    /** Limite por hora cuando la key entrante es TEST. */
    public static final int LIMIT_TEST = 50;

    private static final long WINDOW_SECONDS = 3600;
    private static final String AAEF_PATH_SUFFIX = "/api/contabilidad/aaef";

    private final ParameterRepository parameterRepository;

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
        int limit = resolveLimit(request);

        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        Deque<Instant> dq = windowByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (dq) {
            // Purgar timestamps fuera de ventana
            while (!dq.isEmpty() && dq.peekFirst().isBefore(windowStart)) {
                dq.pollFirst();
            }
            if (dq.size() >= limit) {
                long waitSeconds = WINDOW_SECONDS - Duration.between(dq.peekFirst(), now).toSeconds();
                long waitMinutes = Math.max(1, waitSeconds / 60);
                log.warn("AAEF rate limit excedido para key={} (limite={}), esperar {} min",
                        key, limit, waitMinutes);
                response.setStatus(429); // TOO_MANY_REQUESTS
                response.setHeader("Retry-After", String.valueOf(waitSeconds));
                response.setContentType("application/json");
                response.getWriter().write(String.format(
                    "{\"success\":false,\"code\":429,\"errorCode\":\"RATE_LIMIT_EXCEEDED\","
                  + "\"message\":\"Rate limit excedido: maximo %d lotes por hora. Intente nuevamente en %d minutos.\"}",
                    limit, waitMinutes));
                return;
            }
            dq.addLast(now);
        }
        chain.doFilter(request, response);
    }

    /**
     * Resuelve el limite por hora segun la API Key del header (PRODUCTION vs TEST).
     * Si la key no coincide con ninguna o no se pudo leer la BD, aplica el limite
     * PRODUCTION como fallback defensivo (ApiKeyFilter rechazara con 401 igualmente).
     */
    private int resolveLimit(HttpServletRequest request) {
        String providedKey = request.getHeader(ApiKeyFilter.API_KEY_HEADER);
        if (providedKey == null || providedKey.isBlank()) {
            return LIMIT_PRODUCTION;
        }
        try {
            Optional<String> testKey = parameterRepository
                    .findGlobalValueByName(ApiKeyFilter.API_KEY_TEST_PARAMETER_NAME);
            if (testKey.isPresent() && !testKey.get().isBlank()
                    && constantTimeEquals(providedKey, testKey.get())) {
                return LIMIT_TEST;
            }
        } catch (Exception e) {
            log.warn("No se pudo resolver tier de API Key, aplicando limite PRODUCTION", e);
        }
        return LIMIT_PRODUCTION;
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

    /** Comparacion de tiempo constante para no filtrar por timing entre PROD y TEST. */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

package com.sigcon.backend.general.security;

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
 * PA-RNF-10 (Pendientes PA, 2026-06-03) punto 1: proteccion contra fuerza bruta
 * por IP sobre {@code POST /auth/login}.
 *
 * <p>Limita a {@value #MAX_PER_WINDOW} intentos por minuto desde la misma IP.
 * Al superarse devuelve {@code HTTP 429} con header {@code Retry-After: 60} y un
 * cuerpo JSON con el mensaje. Esto se suma (no reemplaza) al bloqueo por usuario
 * (failed_login_attempts / locked_until) que ya implementa {@code AuthService}:
 * el rate limit por IP frena un atacante que rota usuarios desde una sola IP.
 *
 * <p>Implementacion: sliding window en memoria por IP (deque de timestamps).
 * Suficiente para single-instance / dev (lo recomienda el RF). Si en el futuro
 * se escala horizontalmente, migrar a Redis o Bucket4j. Mismo patron que
 * {@code AaefRateLimitFilter}.
 *
 * <p>Se registra en {@code SecurityConfig} con
 * {@code addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)}.
 */
@Slf4j
@Component
public class IpLoginRateLimitFilter extends OncePerRequestFilter {

    /** Maximo de intentos de login por IP dentro de la ventana. */
    public static final int MAX_PER_WINDOW = 10;

    /** Ventana deslizante en segundos (1 minuto). */
    public static final long WINDOW_SECONDS = 60;

    private static final String LOGIN_PATH_SUFFIX = "/auth/login";

    /** Map IP -> ventana deslizante de timestamps (last 1 min). */
    private final Map<String, Deque<Instant>> windowByIp = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return true;
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        // Solo el endpoint de login (soporta prefijo de proxy tipo /sigcon/dev).
        return !(path.endsWith(LOGIN_PATH_SUFFIX) || path.endsWith(LOGIN_PATH_SUFFIX + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = resolveIp(request);

        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        Deque<Instant> dq = windowByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && dq.peekFirst().isBefore(windowStart)) {
                dq.pollFirst();
            }
            if (dq.size() >= MAX_PER_WINDOW) {
                long waitSeconds = Math.max(1,
                        WINDOW_SECONDS - Duration.between(dq.peekFirst(), now).toSeconds());
                log.warn("PA-RNF-10 rate limit de login excedido para ip={} ({}/{} en {}s), esperar {}s",
                        ip, dq.size(), MAX_PER_WINDOW, WINDOW_SECONDS, waitSeconds);
                response.setStatus(429); // TOO_MANY_REQUESTS
                response.setHeader("Retry-After", String.valueOf(waitSeconds));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(String.format(
                        "{\"success\":false,\"code\":429,\"errorCode\":\"LOGIN_RATE_LIMIT\","
                      + "\"requireCaptcha\":true,"
                      + "\"message\":\"Demasiados intentos de inicio de sesion desde esta direccion. "
                      + "Intente nuevamente en %d segundos.\",\"msg\":\"Demasiados intentos de inicio de sesion "
                      + "desde esta direccion. Intente nuevamente en %d segundos.\"}",
                        waitSeconds, waitSeconds));
                return;
            }
            dq.addLast(now);
        }
        chain.doFilter(request, response);
    }

    /** IP real del cliente respetando proxy (X-Forwarded-For, primer hop). */
    private String resolveIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}

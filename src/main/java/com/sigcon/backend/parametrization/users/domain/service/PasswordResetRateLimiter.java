package com.sigcon.backend.parametrization.users.domain.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PA-RF-02 v3.0 (Control de Cambios PA, 2026-05-29): rate limiter en memoria
 * para la recuperacion de contrasena (control anti-abuso).
 *
 * <p>Ventana deslizante doble: por email y por IP. Sin dependencia de Redis
 * (instancia unica). Al reiniciar el backend el estado se reinicia, lo cual es
 * aceptable para esta proteccion.
 */
@Component
public class PasswordResetRateLimiter {

    /** Maximo de solicitudes por email dentro de la ventana. */
    public static final int MAX_PER_EMAIL = 3;
    /** Maximo de solicitudes por IP dentro de la ventana. */
    public static final int MAX_PER_IP = 8;
    /** Ventana de tiempo en minutos. */
    public static final long WINDOW_MINUTES = 15;

    private final Map<String, Deque<LocalDateTime>> emailHits = new ConcurrentHashMap<>();
    private final Map<String, Deque<LocalDateTime>> ipHits = new ConcurrentHashMap<>();

    /**
     * Registra el intento y retorna {@code true} si esta dentro del limite,
     * {@code false} si lo excede (por email O por IP).
     */
    public boolean allow(String email, String ip) {
        boolean okEmail = (email == null || email.isBlank())
                || hit(emailHits, email.toLowerCase().trim(), MAX_PER_EMAIL);
        boolean okIp = (ip == null || ip.isBlank())
                || hit(ipHits, ip, MAX_PER_IP);
        return okEmail && okIp;
    }

    private boolean hit(Map<String, Deque<LocalDateTime>> map, String key, int max) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(WINDOW_MINUTES);
        Deque<LocalDateTime> dq = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && dq.peekFirst().isBefore(cutoff)) {
                dq.pollFirst();
            }
            if (dq.size() >= max) {
                return false;
            }
            dq.addLast(now);
            return true;
        }
    }
}

package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.parametrization.users.domain.model.UserSession;
import com.sigcon.backend.parametrization.users.domain.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PA-RF-01 v3.0 (Control de Cambios PA, 2026-05-29): gestion de sesiones
 * activas y refresh tokens.
 *
 * <ul>
 *   <li><b>Limite FIFO:</b> maximo {@link #MAX_ACTIVE_SESSIONS} sesiones activas
 *       por usuario. Al crear una nueva que excede el limite, se revocan las mas
 *       antiguas (estrategia FIFO, PA-RF-01 punto 2).</li>
 *   <li><b>Refresh token:</b> alto-entropia, persistido como hash SHA-256, con
 *       expiracion de {@link #REFRESH_TOKEN_DAYS} dias (PA-RF-01 punto 1).</li>
 *   <li><b>Revocacion masiva:</b> {@link #revokeAllForUser} / {@link #revokeAllForUsers}
 *       usados al restablecer contrasena (PA-RF-02), desactivar empresa
 *       (PA-RF-PLAT-03) o PLATFORM_ADMIN (PA-RF-PLAT-07).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    public static final int MAX_ACTIVE_SESSIONS = 3;
    public static final long REFRESH_TOKEN_DAYS = 7;

    private final UserSessionRepository sessionRepository;

    /** Resultado de crear una sesion: el refresh token en claro se entrega solo una vez. */
    public record IssuedSession(String sessionId, String refreshToken, LocalDateTime expiresAt) {}

    /**
     * Crea una sesion para el usuario aplicando el limite FIFO de
     * {@link #MAX_ACTIVE_SESSIONS}. Si el usuario ya tiene ese maximo de sesiones
     * activas, revoca las mas antiguas hasta dejar espacio para la nueva.
     */
    @Transactional
    public IssuedSession createSession(Long userId, String deviceId, String userAgent, String ip) {
        // FIFO (PA-RF-01 punto 2): dejar a lo sumo MAX_ACTIVE_SESSIONS - 1 activas
        // antes de insertar la nueva, revocando las mas antiguas.
        List<UserSession> active = sessionRepository.findByUserIdAndRevokedAtIsNullOrderByIssuedAtAsc(userId);
        int toRevoke = active.size() - (MAX_ACTIVE_SESSIONS - 1);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < toRevoke && i < active.size(); i++) {
            UserSession old = active.get(i);
            old.setRevokedAt(now);
            sessionRepository.save(old);
        }

        String sessionId = UUID.randomUUID().toString();
        String rawRefresh = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        UserSession s = UserSession.builder()
                .userId(userId)
                .sessionId(sessionId)
                .refreshTokenHash(sha256(rawRefresh))
                .deviceId(trunc(deviceId, 200))
                .userAgent(trunc(userAgent, 512))
                .ipAddress(trunc(ip, 64))
                .issuedAt(now)
                .expiresAt(now.plusDays(REFRESH_TOKEN_DAYS))
                .lastUsedAt(now)
                .build();
        sessionRepository.save(s);
        return new IssuedSession(sessionId, rawRefresh, s.getExpiresAt());
    }

    /** Valida un refresh token en claro: existe, no revocado, no expirado. */
    public Optional<UserSession> validateRefreshToken(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) return Optional.empty();
        return sessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(sha256(rawRefresh))
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Transactional
    public void touch(UserSession s) {
        s.setLastUsedAt(LocalDateTime.now());
        sessionRepository.save(s);
    }

    @Transactional
    public void revokeBySessionId(String sessionId) {
        if (sessionId == null) return;
        sessionRepository.findBySessionIdAndRevokedAtIsNull(sessionId).ifPresent(s -> {
            s.setRevokedAt(LocalDateTime.now());
            sessionRepository.save(s);
        });
    }

    /** Revoca TODAS las sesiones activas de un usuario. Retorna cuantas se revocaron. */
    @Transactional
    public int revokeAllForUser(Long userId) {
        List<UserSession> active = sessionRepository.findByUserIdAndRevokedAtIsNullOrderByIssuedAtAsc(userId);
        LocalDateTime now = LocalDateTime.now();
        active.forEach(s -> s.setRevokedAt(now));
        sessionRepository.saveAll(active);
        return active.size();
    }

    /** Revoca todas las sesiones activas de un conjunto de usuarios. Retorna el total revocado. */
    @Transactional
    public int revokeAllForUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return 0;
        List<UserSession> active = sessionRepository.findByUserIdInAndRevokedAtIsNull(userIds);
        LocalDateTime now = LocalDateTime.now();
        active.forEach(s -> s.setRevokedAt(now));
        sessionRepository.saveAll(active);
        return active.size();
    }

    private static String trunc(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }

    /** SHA-256 hex de un string (para no almacenar el refresh token en claro). */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

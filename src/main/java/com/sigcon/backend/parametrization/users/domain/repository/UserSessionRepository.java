package com.sigcon.backend.parametrization.users.domain.repository;

import com.sigcon.backend.parametrization.users.domain.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * PA-RF-01 v3.0: acceso a las sesiones activas de los usuarios.
 */
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /** Sesiones activas (no revocadas) de un usuario, mas antiguas primero (para FIFO). */
    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByIssuedAtAsc(Long userId);

    long countByUserIdAndRevokedAtIsNull(Long userId);

    Optional<UserSession> findBySessionIdAndRevokedAtIsNull(String sessionId);

    Optional<UserSession> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    /** Sesiones activas de un conjunto de usuarios (revocacion masiva al desactivar empresa). */
    List<UserSession> findByUserIdInAndRevokedAtIsNull(List<Long> userIds);
}

package com.sigcon.backend.parametrization.users.domain.repository;


import com.sigcon.backend.parametrization.users.domain.model.BlackListedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface BlackListedTokenRepository extends JpaRepository<BlackListedToken, Long> {

    boolean existsByToken(String token);
    Optional<BlackListedToken> findByToken(String token);

    /**
     * PA-RF-27 (Pendientes PA): borra las entradas cuyo token ya expiro (claim
     * exp < now). Lo invoca {@code BlacklistCleanupScheduler} a diario para que
     * la blacklist no crezca sin limite. Solo afecta filas con expires_at no
     * nulo (las creadas antes de PA-RF-27 no lo tienen y se ignoran).
     */
    @Modifying
    @Query("delete from BlackListedToken b where b.expiresAt is not null and b.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

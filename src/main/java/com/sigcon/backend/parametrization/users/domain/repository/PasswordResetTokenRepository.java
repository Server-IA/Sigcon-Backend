package com.sigcon.backend.parametrization.users.domain.repository;

import com.sigcon.backend.parametrization.users.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenAndUsedFalse(String token);

    /**
     * PA-RF-02 punto 3 (v3.0): tokens de recuperacion activos (no usados) de un
     * usuario. Se invalidan al emitir un token nuevo (token unico por usuario).
     */
    List<PasswordResetToken> findByUser_IdAndUsedFalse(Long userId);
}

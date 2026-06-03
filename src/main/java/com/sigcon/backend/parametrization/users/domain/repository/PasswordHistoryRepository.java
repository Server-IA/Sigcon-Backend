package com.sigcon.backend.parametrization.users.domain.repository;

import com.sigcon.backend.parametrization.users.domain.model.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * PA-RF-01 v3.0: acceso al historial de contrasenas para validar la regla de
 * "no reutilizar las ultimas 5".
 */
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /** Ultimas 5 contrasenas del usuario, mas recientes primero. */
    List<PasswordHistory> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);
}

package com.sigcon.backend.integration.apikeys.domain.repository;

import com.sigcon.backend.integration.apikeys.domain.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PA-RF-28 (Pendientes PA): repositorio de credenciales AAEF.
 *
 * <p>La entidad no tiene {@code @Filter} de tenant, asi que estas consultas
 * operan cross-empresa (PLATFORM_ADMIN). Los metodos por companyId acotan al
 * scope cuando se requiere.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** Validacion en el ApiKeyFilter: clave ACTIVE por su hash. */
    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, String status);

    /** Regla de maximo 2 activas por empresa. */
    long countByCompanyIdAndStatus(Long companyId, String status);

    /** Listado de metadata por empresa (mas reciente primero). */
    List<ApiKey> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    /** Scheduler: claves ACTIVE ya vencidas (para marcarlas EXPIRED). */
    List<ApiKey> findByStatusAndExpiresAtBefore(String status, LocalDateTime cutoff);

    /** Scheduler: claves ACTIVE proximas a expirar y aun no avisadas. */
    List<ApiKey> findByStatusAndNotifiedExpiryFalseAndExpiresAtBetween(
            String status, LocalDateTime from, LocalDateTime to);
}

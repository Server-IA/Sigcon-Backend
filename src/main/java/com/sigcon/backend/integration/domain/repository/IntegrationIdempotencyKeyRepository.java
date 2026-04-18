package com.sigcon.backend.integration.domain.repository;

import com.sigcon.backend.integration.domain.model.IntegrationIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para {@link IntegrationIdempotencyKey}.
 *
 * <p>No hace soft delete: los registros de idempotencia son permanentes para
 * garantizar que ningun lote se procese dos veces, nunca.
 */
public interface IntegrationIdempotencyKeyRepository
        extends JpaRepository<IntegrationIdempotencyKey, Long> {

    Optional<IntegrationIdempotencyKey> findByExchangeIdAndStandardVersion(
            String exchangeId, String standardVersion);

    boolean existsByExchangeIdAndStandardVersion(String exchangeId, String standardVersion);
}

package com.sigcon.backend.integration.domain.repository;

import com.sigcon.backend.integration.domain.model.IntegrationTransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio del historial inmutable de intentos por transfer (HU-INT-RF-15 E4).
 */
@Repository
public interface IntegrationTransferHistoryRepository
        extends JpaRepository<IntegrationTransferHistory, Long> {

    /** Historial completo de un transfer ordenado cronologicamente. */
    List<IntegrationTransferHistory> findByTransferIdOrderByOccurredAtAsc(Long transferId);

    /** Conteo de intentos para un transfer (incluido el inicial). */
    long countByTransferId(Long transferId);
}

package com.sigcon.backend.third_parties.change_history.domain.repository;

import com.sigcon.backend.third_parties.change_history.domain.model.ThirdPartyChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio para el historial de cambios de terceros.
 */
public interface ThirdPartyChangeHistoryRepository extends JpaRepository<ThirdPartyChangeHistory, Long> {

    /**
     * Obtiene el historial de cambios de un tercero ordenado por fecha descendente.
     *
     * @param thirdPartyId ID del tercero
     * @return lista de registros de cambios
     */
    List<ThirdPartyChangeHistory> findByThirdPartyIdOrderByChangedAtDesc(Long thirdPartyId);
}

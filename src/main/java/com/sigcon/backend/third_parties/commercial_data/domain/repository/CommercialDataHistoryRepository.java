package com.sigcon.backend.third_parties.commercial_data.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.third_parties.commercial_data.domain.model.CommercialDataHistory;

/**
 * Repositorio para registros de historial de cambios en datos comerciales.
 */
@Repository
public interface CommercialDataHistoryRepository extends JpaRepository<CommercialDataHistory, Long> {

    /**
     * Busca el historial de cambios de un registro de datos comerciales,
     * ordenado por fecha de cambio descendente.
     *
     * @param commercialDataId ID del registro de datos comerciales
     * @return lista de cambios ordenados del mas reciente al mas antiguo
     */
    List<CommercialDataHistory> findByCommercialDataIdOrderByChangedAtDesc(Long commercialDataId);
}

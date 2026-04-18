package com.sigcon.backend.accounts_receivable.advances.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.accounts_receivable.advances.domain.model.ArAdvance;

/**
 * Repositorio JPA para la entidad {@link ArAdvance}.
 * Provee consultas para anticipos de clientes.
 */
public interface ArAdvanceRepository extends JpaRepository<ArAdvance, Long>, JpaSpecificationExecutor<ArAdvance> {

    /**
     * Obtiene los anticipos de un tercero filtrados por estado.
     *
     * @param thirdPartyId identificador del tercero
     * @param status       estado del anticipo
     * @return lista de anticipos que coinciden con los criterios
     */
    List<ArAdvance> findByThirdPartyIdAndStatusAndDeletedAtIsNull(Long thirdPartyId, String status);
}

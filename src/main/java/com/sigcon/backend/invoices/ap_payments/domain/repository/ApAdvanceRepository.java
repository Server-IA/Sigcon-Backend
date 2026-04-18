package com.sigcon.backend.invoices.ap_payments.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.invoices.ap_payments.domain.model.ApAdvance;

/**
 * Repositorio JPA para la entidad {@link ApAdvance}.
 * Provee consultas para anticipos a proveedores.
 */
public interface ApAdvanceRepository extends JpaRepository<ApAdvance, Long>, JpaSpecificationExecutor<ApAdvance> {

    /**
     * Obtiene los anticipos de un tercero filtrados por estado.
     *
     * @param thirdPartyId identificador del tercero
     * @param status       estado del anticipo (PENDING, APPLIED)
     * @return lista de anticipos que coinciden con los criterios
     */
    List<ApAdvance> findByThirdPartyIdAndStatusAndDeletedAtIsNull(Long thirdPartyId, String status);
}

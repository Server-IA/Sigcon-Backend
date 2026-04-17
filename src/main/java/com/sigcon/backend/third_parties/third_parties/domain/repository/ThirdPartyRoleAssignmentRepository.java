package com.sigcon.backend.third_parties.third_parties.domain.repository;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * TER-04: Repositorio para asignaciones de roles con vigencia temporal.
 */
public interface ThirdPartyRoleAssignmentRepository extends JpaRepository<ThirdPartyRoleAssignment, Long> {

    /**
     * Obtiene todas las asignaciones de roles activas (no eliminadas) de un tercero.
     *
     * @param thirdPartyId ID del tercero
     * @return lista de asignaciones de roles
     */
    List<ThirdPartyRoleAssignment> findByThirdPartyIdAndDeletedAtIsNull(Long thirdPartyId);

    /**
     * Obtiene las asignaciones de roles vigentes (sin fecha de fin) de un tercero.
     *
     * @param thirdPartyId ID del tercero
     * @return lista de asignaciones vigentes
     */
    List<ThirdPartyRoleAssignment> findByThirdPartyIdAndValidToIsNullAndDeletedAtIsNull(Long thirdPartyId);
}

package com.sigcon.backend.banks.matching.domain.repository;

import com.sigcon.backend.banks.matching.domain.model.ReglaClasificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * BNK-HU-071: acceso a reglas de clasificación. El pre-procesamiento (HU-068)
 * consume las activas ordenadas por prioridad ASC.
 */
public interface ReglaClasificacionRepository extends JpaRepository<ReglaClasificacion, Long> {

    List<ReglaClasificacion> findByDeletedAtIsNullOrderByPrioridadAsc();

    List<ReglaClasificacion> findByActivaTrueAndDeletedAtIsNullOrderByPrioridadAsc();

    Optional<ReglaClasificacion> findByIdAndDeletedAtIsNull(Long id);

    long countByPrioridadAndActivaTrueAndDeletedAtIsNull(Integer prioridad);
}

package com.sigcon.backend.parametrization.resources.domain.repository;

import com.sigcon.backend.parametrization.resources.domain.model.SystemWithholdingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Repositorio para asignaciones de retenciones del sistema.
 */
public interface SystemWithholdingAssignmentRepository
        extends JpaRepository<SystemWithholdingAssignment, Long>,
                JpaSpecificationExecutor<SystemWithholdingAssignment> {

    /**
     * Verifica si existe una asignacion activa (no eliminada) para una retencion dada.
     *
     * @param withholdingId ID de la retencion
     * @param status        estado de la asignacion
     * @return true si ya existe una asignacion activa
     */
    boolean existsByWithholdingIdAndStatusAndDeletedAtIsNull(Long withholdingId, String status);

    /**
     * Obtiene todas las asignaciones con un estado dado que no esten eliminadas.
     *
     * @param status estado a filtrar
     * @return lista de asignaciones
     */
    List<SystemWithholdingAssignment> findByStatusAndDeletedAtIsNull(String status);
}

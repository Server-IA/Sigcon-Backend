package com.sigcon.backend.bnk_cash_flow.domain.repository;

import com.sigcon.backend.bnk_cash_flow.domain.model.CashFlowProjection;
import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * BNK-RF-29 / BNK-RF-32 — Repositorio de proyecciones de flujo de caja.
 *
 * Todos los métodos utilizan "deletedAt IS NULL" implícitamente
 * gracias al filtro global @Where de la entidad.
 *
 * No se expone ningún método delete() para evitar eliminación física.
 */
public interface CashFlowProjectionRepository
        extends JpaRepository<CashFlowProjection, Long>,
                JpaSpecificationExecutor<CashFlowProjection> {

    /**
     * BNK-RF-29: Validar unicidad del nombre al crear.
     */
    boolean existsByNameAndDeletedAtIsNull(String name);

    /**
     * BNK-RF-30: Validar unicidad del nombre al actualizar (excluye el propio registro).
     */
    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);

    /**
     * BNK-RF-32: Buscar por ID garantizando que no fue eliminado lógicamente.
     */
    Optional<CashFlowProjection> findByIdAndDeletedAtIsNull(Long id);

    /**
     * BNK-RF-32: Buscar todas las proyecciones por estado (excluye eliminadas lógicamente).
     */
    List<CashFlowProjection> findByStatusAndDeletedAtIsNull(ProjectionStatus status);

    /**
     * BNK-RF-32: Buscar por nombre (parcial, sin distinguir mayúsculas).
     */
    List<CashFlowProjection> findByNameContainingIgnoreCaseAndDeletedAtIsNull(String name);
}

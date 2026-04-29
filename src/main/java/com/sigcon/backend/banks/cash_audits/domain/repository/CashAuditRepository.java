package com.sigcon.backend.banks.cash_audits.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.banks.cash_audits.domain.model.CashAudit;
import com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus;

/**
 * Repositorio JPA para arqueos de caja.
 * Soporta paginacion dinamica via JpaSpecificationExecutor (DataTable pattern).
 */
@Repository
public interface CashAuditRepository extends JpaRepository<CashAudit, Long>,
        JpaSpecificationExecutor<CashAudit> {

    /**
     * Busca arqueos de una caja por estado especifico.
     */
    List<CashAudit> findByCashIdAndStatusAndDeletedAtIsNull(Long cashId, CashAuditStatus status);

    /**
     * Verifica si existen arqueos abiertos o en revision para una caja dada.
     */
    boolean existsByCashIdAndStatusInAndDeletedAtIsNull(Long cashId, List<CashAuditStatus> statuses);

    /**
     * HU-BNK-048 - Verifica si la caja tiene arqueos historicos no anulados
     * (cualquier arqueo que no haya sido eliminado fisicamente ni anulado).
     * Usado para bloquear eliminacion fisica de la caja.
     */
    boolean existsByCashIdAndDeletedAtIsNull(Long cashId);
}


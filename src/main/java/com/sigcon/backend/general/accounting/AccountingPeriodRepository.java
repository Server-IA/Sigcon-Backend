package com.sigcon.backend.general.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de periodos contables con soporte para consultas dinamicas
 * mediante JpaSpecificationExecutor.
 */
public interface AccountingPeriodRepository
        extends JpaRepository<AccountingPeriod, Long>,
                JpaSpecificationExecutor<AccountingPeriod> {

    Optional<AccountingPeriod> findByYearAndMonth(Integer year, Integer month);

    /** Obtiene todos los periodos de un anio ordenados por mes. */
    List<AccountingPeriod> findByYear(Integer year);

    /** Obtiene todos los periodos con un estado especifico. */
    List<AccountingPeriod> findByStatus(AccountingPeriodStatus status);
}

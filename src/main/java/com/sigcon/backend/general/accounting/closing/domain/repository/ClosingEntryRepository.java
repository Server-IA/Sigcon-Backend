package com.sigcon.backend.general.accounting.closing.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.general.accounting.closing.domain.model.ClosingEntry;
import com.sigcon.backend.general.accounting.closing.domain.model.enums.ClosingStatus;
import com.sigcon.backend.general.accounting.closing.domain.model.enums.ClosingType;

/**
 * Repositorio para la entidad ClosingEntry (registros de cierre contable).
 */
public interface ClosingEntryRepository extends JpaRepository<ClosingEntry, Long> {

    /**
     * Verifica si ya existe un cierre para un periodo y tipo especifico.
     * Usado para evitar cierres duplicados.
     */
    boolean existsByFiscalYearAndFiscalMonthAndClosingTypeAndStatusAndDeletedAtIsNull(
            Integer fiscalYear, Integer fiscalMonth, ClosingType closingType, ClosingStatus status);

    /**
     * Busca un cierre especifico por periodo y tipo.
     */
    Optional<ClosingEntry> findByFiscalYearAndFiscalMonthAndClosingTypeAndStatusAndDeletedAtIsNull(
            Integer fiscalYear, Integer fiscalMonth, ClosingType closingType, ClosingStatus status);

    /**
     * Lista todos los cierres de un anio fiscal.
     */
    List<ClosingEntry> findByFiscalYearAndDeletedAtIsNullOrderByFiscalMonthAsc(Integer fiscalYear);
}

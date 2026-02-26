package com.sigcon.backend.lists_accounting.exchangeRates.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeRate;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeType;

import java.time.LocalDate;
import java.util.List;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    // VALIDACIÓN PARA CREAR
    @Query("""
        SELECT COUNT(e) > 0
        FROM ExchangeRate e
        WHERE e.currencyId = :currencyId
        AND e.exchangeType = :type
        AND e.deletedAt IS NULL
        AND (:startDate <= e.endDate AND :endDate >= e.startDate)
    """)
    boolean existsOverlap(
            Long currencyId,
            ExchangeType type,
            LocalDate startDate,
            LocalDate endDate
    );

    // VALIDACIÓN PARA EDITAR (IGNORA EL MISMO REGISTRO)
    @Query("""
        SELECT COUNT(e) > 0
        FROM ExchangeRate e
        WHERE e.currencyId = :currencyId
        AND e.exchangeType = :type
        AND e.deletedAt IS NULL
        AND e.id <> :id
        AND (:startDate <= e.endDate AND :endDate >= e.startDate)
    """)
    boolean existsOverlapForUpdate(
            Long currencyId,
            ExchangeType type,
            LocalDate startDate,
            LocalDate endDate,
            Long id
    );

    List<ExchangeRate> findByDeletedAtIsNull();
}
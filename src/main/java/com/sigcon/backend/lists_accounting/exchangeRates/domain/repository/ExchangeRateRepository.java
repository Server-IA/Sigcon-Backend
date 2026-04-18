package com.sigcon.backend.lists_accounting.exchangeRates.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeRate;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.ExchangeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("""
        SELECT e
        FROM ExchangeRate e
        WHERE e.currencyExchange.id = :currencyId
        OR e.currencyExchanged.id = :currencyId
        AND e.deletedAt IS NULL
    """)
    Optional<ExchangeRate> findByCurrencyExchangeOrCurrencyExchanged(Long currencyId);

    /** Validacion de solapamiento para crear (sin companyId, mono-empresa) */
    @Query("""
        SELECT COUNT(e) > 0
        FROM ExchangeRate e
        WHERE e.currencyExchange.id = :currencyId
        AND e.currencyExchanged.id = :currencyExchangedId
        AND e.exchangeType = :type
        AND e.deletedAt IS NULL
        AND (:startDate <= e.endDate AND :endDate >= e.startDate)
    """)
    boolean existsOverlap(
            Long currencyId,
            Long currencyExchangedId,
            ExchangeType type,
            LocalDate startDate,
            LocalDate endDate
    );

    // VALIDACIÓN PARA EDITAR (IGNORA EL MISMO REGISTRO)
    @Query("""
        SELECT COUNT(e) > 0
        FROM ExchangeRate e
        WHERE e.currencyExchange.id = :currencyId
        AND e.currencyExchanged.id = :currencyExchangedId
        AND e.exchangeType = :type
        AND e.deletedAt IS NULL
        AND e.id <> :id
        AND (:startDate <= e.endDate AND :endDate >= e.startDate)
    """)
    boolean existsOverlapForUpdate(
            Long currencyId,
            Long currencyExchangedId,
            ExchangeType type,
            LocalDate startDate,
            LocalDate endDate,
            Long id
    );

    List<ExchangeRate> findByDeletedAtIsNull();
    Page<ExchangeRate> findAll(Specification<ExchangeRate> spec, Pageable pageable);
}
package com.sigcon.backend.banks.trm.domain.repository;

import com.sigcon.backend.banks.trm.domain.model.TrmHistorica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrmHistoricaRepository extends JpaRepository<TrmHistorica, Long> {

    /** TRM exacta de una fecha y moneda (para upsert manual). */
    Optional<TrmHistorica> findByCurrencyIsoAndFechaAndDeletedAtIsNull(String currencyIso, LocalDate fecha);

    /** TRM vigente para una fecha = la más reciente con fecha &lt;= la dada (HU-076 E2/E5). */
    Optional<TrmHistorica> findTopByCurrencyIsoAndFechaLessThanEqualAndDeletedAtIsNullOrderByFechaDesc(
            String currencyIso, LocalDate fecha);

    /** Última TRM publicada de una moneda (para el carry-forward del job). */
    Optional<TrmHistorica> findTopByCurrencyIsoAndDeletedAtIsNullOrderByFechaDesc(String currencyIso);

    /** Histórico por moneda en un rango de fechas. */
    List<TrmHistorica> findByCurrencyIsoAndFechaBetweenAndDeletedAtIsNullOrderByFechaDesc(
            String currencyIso, LocalDate desde, LocalDate hasta);

    /** Histórico completo por moneda (orden descendente). */
    List<TrmHistorica> findByCurrencyIsoAndDeletedAtIsNullOrderByFechaDesc(String currencyIso);

    /** Todas las TRM de una fecha (para el job: saber qué monedas ya tienen dato hoy). */
    List<TrmHistorica> findByFechaAndDeletedAtIsNull(LocalDate fecha);

    /** Monedas distintas que tienen al menos una TRM cargada (para el carry-forward del job). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT t.currencyIso FROM TrmHistorica t WHERE t.deletedAt IS NULL")
    List<String> findDistinctCurrencies();
}

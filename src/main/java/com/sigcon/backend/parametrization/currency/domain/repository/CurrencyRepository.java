package com.sigcon.backend.parametrization.currency.domain.repository;

import com.sigcon.backend.parametrization.currency.domain.model.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByIsoCodeAndDeletedAtIsNull(String isoCode);

    boolean existsByIdAndDeletedAtIsNull(Long id);

}
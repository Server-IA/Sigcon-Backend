package com.sigcon.backend.accounting_lists.types_of_currency.domain.repository;

import com.sigcon.backend.accounting_lists.types_of_currency.domain.model.CurrencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyTypeRepository
        extends JpaRepository<CurrencyType, Long>, JpaSpecificationExecutor<CurrencyType> {

    boolean existsByIsoCodeAndDeletedAtIsNull(String isoCode);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByIsoCodeAndIdNotAndDeletedAtIsNull(String isoCode, Long id);

    boolean existsByNameIgnoreCaseAndIdNotAndDeletedAtIsNull(String name, Long id);

    Optional<CurrencyType> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    // TODO: Replace this with the actual query when transactional tables exist
    default boolean isCurrencyUsed(Long currencyId) {
        return false;
    }
}

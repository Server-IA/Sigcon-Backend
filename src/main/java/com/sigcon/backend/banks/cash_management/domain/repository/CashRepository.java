package com.sigcon.backend.banks.cash_management.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.cash_management.domain.model.enums.CashStatus;

@Repository
public interface CashRepository extends JpaRepository<Cash, Long>, 
        JpaSpecificationExecutor<Cash> {

    // BNK-RF-10: Validar unicidad del código de caja por empresa
    boolean existsByCashCodeAndDeletedAtIsNull(String cashCode);

    // BNK-RF-10: Validar unicidad del código al editar (excluyendo la caja actual)
    boolean existsByCashCodeAndIdNotAndDeletedAtIsNull(String cashCode, Long id);

    // BNK-RF-11 / BNK-RF-12 / BNK-RF-13: Buscar caja vigente por ID
    Optional<Cash> findByIdAndDeletedAtIsNull(Long id);

    // BNK-RF-12: Verificar si una caja tiene un estado específico
    boolean existsByIdAndCashStatusAndDeletedAtIsNull(Long id, CashStatus cashStatus);
}

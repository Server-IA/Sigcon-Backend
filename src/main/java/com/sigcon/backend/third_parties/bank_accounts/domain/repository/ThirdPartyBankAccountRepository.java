package com.sigcon.backend.third_parties.bank_accounts.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.third_parties.bank_accounts.domain.model.ThirdPartyBankAccount;

/**
 * Repositorio para vinculaciones de cuentas bancarias a terceros.
 */
@Repository
public interface ThirdPartyBankAccountRepository extends JpaRepository<ThirdPartyBankAccount, Long> {

    /**
     * Lista todas las vinculaciones activas de un tercero.
     */
    List<ThirdPartyBankAccount> findByThirdPartyIdAndDeletedAtIsNull(Long thirdPartyId);

    /**
     * Verifica si ya existe una vinculacion activa entre un tercero y una cuenta bancaria.
     */
    boolean existsByThirdPartyIdAndBankAccountIdAndDeletedAtIsNull(Long thirdPartyId, Long bankAccountId);

    /**
     * HU-TER-05 (2026-04-27): Lista todas las vinculaciones activas de una
     * cuenta bancaria (lookup inverso). Permite mostrar desde el modulo BNK
     * que terceros tienen asociada una cuenta bancaria especifica.
     */
    List<ThirdPartyBankAccount> findByBankAccountIdAndDeletedAtIsNull(Long bankAccountId);
}

package com.sigcon.backend.banks.checks.domain.repository;

import com.sigcon.backend.banks.checks.domain.model.Check;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CheckRepository extends JpaRepository<Check, Long>, JpaSpecificationExecutor<Check> {

    boolean existsByNumberCheckAndDeletedAtIsNull(Integer numberCheck);

    Optional<Check> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"checkbook", "checkbook.bankAccount"})
    @Query("SELECT c FROM Check c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Check> findWithCheckbookAndBankById(@Param("id") Long id);

    long countByCheckbook_Id(Long checkbookId);

    /**
     * QA Bloque AU (2026-05-06) — Bug 2: cheques en estado activo de la
     * cuenta bancaria. Recorre todas las chequeras de la cuenta y cuenta
     * cheques cuyo statusCheck NO es ANULADO ni COBRADO.
     */
    @Query("SELECT COUNT(c) FROM Check c "
         + "WHERE c.checkbook.bankAccount.id = :bankAccountId "
         + "AND c.deletedAt IS NULL "
         + "AND c.statusCheck NOT IN ('ANULADO', 'COBRADO')")
    long countActiveByBankAccountId(@Param("bankAccountId") Long bankAccountId);
}

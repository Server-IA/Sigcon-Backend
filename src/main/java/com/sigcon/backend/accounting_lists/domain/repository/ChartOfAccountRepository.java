package com.sigcon.backend.accounting_lists.domain.repository;

import com.sigcon.backend.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
SELECT c FROM ChartOfAccount c
WHERE UPPER(c.code) LIKE CONCAT('%', UPPER(COALESCE(:code, '')), '%')
  AND UPPER(c.name) LIKE CONCAT('%', UPPER(COALESCE(:name, '')), '%')
  AND (:accountClass IS NULL OR c.accountClass = :accountClass)
  AND (:accountLevel IS NULL OR c.accountLevel = :accountLevel)
  AND (:accountNature IS NULL OR c.accountNature = :accountNature)
  AND (:status IS NULL OR c.status = :status)
""")
    Page<ChartOfAccount> searchChartOfAccounts(
            @Param("code") String code,
            @Param("name") String name,
            @Param("accountClass") AccountClass accountClass,
            @Param("accountLevel") AccountLevel accountLevel,
            @Param("accountNature") AccountNature accountNature,
            @Param("status") AccountStatus status,
            Pageable pageable
    );


}

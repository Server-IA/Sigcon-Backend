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

    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM cfg_chart_of_accounts c WHERE c.account_code = :code", nativeQuery = true)
    boolean existsAnyByCode(@Param("code") String code);

    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM cfg_chart_of_accounts c WHERE UPPER(c.account_name) = UPPER(:name)", nativeQuery = true)
    boolean existsAnyByName(@Param("name") String name);

    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM cfg_chart_of_accounts c WHERE c.account_code = :code AND c.id <> :id", nativeQuery = true)
    boolean existsAnyByCodeAndIdNot(@Param("code") String code, @Param("id") Long id);

    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM cfg_chart_of_accounts c WHERE UPPER(c.account_name) = UPPER(:name) AND c.id <> :id", nativeQuery = true)
    boolean existsAnyByNameAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM cfg_chart_of_accounts c WHERE c.deleted_at IS NULL AND c.is_deleted = 'NOT_DELETED' AND c.account_code LIKE CONCAT(:codePrefix, '%') AND c.id <> :id", nativeQuery = true)
    boolean existsActiveChildrenByCodePrefix(@Param("codePrefix") String codePrefix, @Param("id") Long id);

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

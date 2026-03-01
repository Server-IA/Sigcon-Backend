package com.sigcon.backend.lists_accounting.accounting_lists.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long>, JpaSpecificationExecutor<ChartOfAccount> {

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

}

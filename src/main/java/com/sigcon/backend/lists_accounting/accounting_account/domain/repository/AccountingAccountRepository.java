package com.sigcon.backend.lists_accounting.accounting_account.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.parametrization.companies.domain.model.Company;

public interface AccountingAccountRepository extends JpaRepository<AccountingAccount, Long>, JpaSpecificationExecutor<AccountingAccount> {

    Optional<AccountingAccount> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
        SELECT a
        FROM AccountingAccount a
        WHERE a.currencyType.id = :currencyTypeId
        AND a.deletedAt IS NULL
    """)
    Optional<AccountingAccount> findByCurrencyType_Id(Long currencyTypeId);

    List<AccountingAccount> findByPucAccount_Id(Long pucId);

    
    boolean existsByCustomNameAndCompanyIdAndDeletedAtIsNull(String customName, Company companyId);
    
    boolean existsByCustomNameAndCompanyIdAndIdNotAndDeletedAtIsNull(String customName, Company companyId, Long idNot);

    AccountingAccount findByCostCenter_Id(Long costCenterId);
    
    
    // TODO: Método para validar dependencias activas (pendiente módulo transacciones)
    // long countActiveDependenciesById(Long id);
}

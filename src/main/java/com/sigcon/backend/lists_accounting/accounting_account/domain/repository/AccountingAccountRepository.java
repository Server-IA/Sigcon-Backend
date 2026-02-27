package com.sigcon.backend.lists_accounting.accounting_account.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;

public interface AccountingAccountRepository extends JpaRepository<AccountingAccount, Long>, JpaSpecificationExecutor<AccountingAccount> {

    Optional<AccountingAccount> findByIdAndDeletedAtIsNull(Long id);
    
    boolean existsByCustomNameAndCompanyIdAndDeletedAtIsNull(String customName, Long companyId);
    
    boolean existsByCustomNameAndCompanyIdAndIdNotAndDeletedAtIsNull(String customName, Long companyId, Long idNot);
    
    // TODO: Método para validar dependencias activas (pendiente módulo transacciones)
    // long countActiveDependenciesById(Long id);
}
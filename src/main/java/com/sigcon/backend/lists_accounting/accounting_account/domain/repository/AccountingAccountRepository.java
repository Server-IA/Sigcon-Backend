package com.sigcon.backend.lists_accounting.accounting_account.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
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

    
    boolean existsByCustomNameAndDeletedAtIsNull(String customName);

    boolean existsByCustomNameAndIdNotAndDeletedAtIsNull(String customName, Long idNot);

    AccountingAccount findByCostCenter_Id(Long costCenterId);

    // HU-CFG-RF-12 E3: cuentas que usan una regla tributaria (impide eliminarla).
    long countByTaxRuleIdAndDeletedAtIsNull(Long taxRuleId);

    /**
     * AAEF v1.1 feedback (2026-04-28): buscar cuenta contable activa del tenant
     * por codigo PUC. Usado para resolver el override {@code accounting_account}
     * que llega en cada Line del documento AAEF.
     *
     * <p>Multi-tenant: filtra explicitamente por {@code company_id} en el JPQL
     * (no podemos delegar al @Filter porque algunas llamadas vienen de flujos
     * donde el filter no esta habilitado durante la consulta).
     *
     * @param pucCode codigo PUC (ej. "1305", "4135")
     * @param companyId tenant actual
     * @return cuenta contable activa del tenant o vacio
     */
    @Query("""
        SELECT a
        FROM AccountingAccount a
        WHERE a.pucAccount.code = :pucCode
        AND a.status = 'ACTIVE'
        AND a.companyId = :companyId
        AND a.deletedAt IS NULL
    """)
    Optional<AccountingAccount> findActiveByPucCodeAndCompany(String pucCode, Long companyId);

    // TODO: Método para validar dependencias activas (pendiente módulo transacciones)
    // long countActiveDependenciesById(Long id);
}

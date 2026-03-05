package com.sigcon.backend.lists_accounting.ruler_tax.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerAccount;

public interface TaxRulerAccountRepository extends JpaRepository<TaxRulerAccount, Long> {

    List<TaxRulerAccount> findByTaxRulerId(Long taxRulerId);
    
    void deleteAllByTaxRulerId(Long taxRulerId);

    List<TaxRulerAccount> findAllByTaxRulerId(Long taxRulerId);
}

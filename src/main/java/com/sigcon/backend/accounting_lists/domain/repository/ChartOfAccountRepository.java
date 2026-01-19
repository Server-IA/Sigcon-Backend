package com.sigcon.backend.accounting_lists.domain.repository;

import com.sigcon.backend.accounting_lists.domain.model.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {
    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);


}

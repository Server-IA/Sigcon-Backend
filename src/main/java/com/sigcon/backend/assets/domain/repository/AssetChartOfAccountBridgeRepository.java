package com.sigcon.backend.assets.domain.repository;

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetChartOfAccountBridgeRepository extends JpaRepository<ChartOfAccount, Long> {

    Optional<ChartOfAccount> findByCode(String code);
}

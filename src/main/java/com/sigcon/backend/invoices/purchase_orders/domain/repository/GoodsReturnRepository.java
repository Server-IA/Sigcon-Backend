package com.sigcon.backend.invoices.purchase_orders.domain.repository;

import com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReturn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReturnRepository extends JpaRepository<GoodsReturn, Long> {
    long countByCompanyId(Long companyId);
}

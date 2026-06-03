package com.sigcon.backend.invoices.purchase_orders.domain.repository;

import com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsReturnRepository
        extends JpaRepository<GoodsReturn, Long>, JpaSpecificationExecutor<GoodsReturn> {
    long countByCompanyId(Long companyId);

    /**
     * RF-18 (Notas Tecnicas CXP, 2026-06-02): MAX del sufijo numerico de los
     * return_number con formato DV-AAAANNNNNN de la empresa, para sincronizar la
     * secuencia por empresa. Query nativo con company_id explicito.
     *
     * @param companyId empresa
     * @return mayor secuencia usada, o 0 si no hay devoluciones
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(RIGHT(return_number, 6) AS INTEGER)), 0) "
            + "FROM goods_returns WHERE company_id = :companyId "
            + "AND return_number ~ '^DV-[0-9]{10}$'", nativeQuery = true)
    long findMaxReturnSequence(@Param("companyId") Long companyId);
}

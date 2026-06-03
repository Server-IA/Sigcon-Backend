package com.sigcon.backend.invoices.purchase_orders.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.invoices.purchase_orders.domain.model.PurchaseOrder;

/**
 * Repositorio JPA para ordenes de compra.
 * Soporta paginacion y filtros dinamicos via JpaSpecificationExecutor.
 */
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    /**
     * Verifica si ya existe una orden con el mismo numero (activa).
     *
     * @param orderNumber numero de la orden
     * @return true si ya existe
     */
    boolean existsByOrderNumberAndDeletedAtIsNull(String orderNumber);

    /**
     * Cuenta las ordenes activas para generar el siguiente consecutivo.
     *
     * @return cantidad total de ordenes (incluyendo eliminadas, para secuencia)
     */
    @Query("SELECT COUNT(po) FROM PurchaseOrder po")
    long countAll();

    /**
     * RF-15 (Notas Tecnicas CXP, 2026-06-02): MAX del sufijo numerico (ultimos 6
     * digitos) de los order_number con formato OC-AAAANNNNNN de la empresa indicada,
     * para sincronizar la secuencia por empresa con los consecutivos ya existentes
     * (incluye seeds). Query nativo: company_id explicito (no depende del @Filter).
     *
     * @param companyId empresa
     * @return mayor secuencia usada, o 0 si no hay ordenes
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(RIGHT(order_number, 6) AS INTEGER)), 0) "
            + "FROM purchase_orders WHERE company_id = :companyId "
            + "AND order_number ~ '^OC-[0-9]{10}$'", nativeQuery = true)
    long findMaxOrderSequence(@Param("companyId") Long companyId);
}

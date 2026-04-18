package com.sigcon.backend.invoices.purchase_orders.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
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
}

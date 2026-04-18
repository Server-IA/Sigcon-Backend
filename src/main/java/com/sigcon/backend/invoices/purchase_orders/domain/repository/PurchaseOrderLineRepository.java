package com.sigcon.backend.invoices.purchase_orders.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.invoices.purchase_orders.domain.model.PurchaseOrderLine;

/**
 * Repositorio JPA para lineas de ordenes de compra.
 */
@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    /**
     * Obtiene todas las lineas activas de una orden de compra.
     *
     * @param purchaseOrderId ID de la orden de compra
     * @return lista de lineas de la orden
     */
    List<PurchaseOrderLine> findByPurchaseOrderId(Long purchaseOrderId);
}

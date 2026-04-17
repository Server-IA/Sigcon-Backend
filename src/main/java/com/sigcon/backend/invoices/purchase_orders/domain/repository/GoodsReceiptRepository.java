package com.sigcon.backend.invoices.purchase_orders.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReceipt;

/**
 * Repositorio JPA para recepciones de bienes/servicios.
 */
@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long>, JpaSpecificationExecutor<GoodsReceipt> {

    /**
     * Obtiene las recepciones activas asociadas a una orden de compra.
     *
     * @param purchaseOrderId ID de la orden de compra
     * @return lista de recepciones
     */
    List<GoodsReceipt> findByPurchaseOrderIdAndDeletedAtIsNull(Long purchaseOrderId);

    /**
     * Verifica si ya existe una recepcion con el mismo numero (activa).
     *
     * @param receiptNumber numero de recepcion
     * @return true si ya existe
     */
    boolean existsByReceiptNumberAndDeletedAtIsNull(String receiptNumber);

    /**
     * Cuenta las recepciones para generar el siguiente consecutivo.
     *
     * @return cantidad total de recepciones
     */
    @Query("SELECT COUNT(gr) FROM GoodsReceipt gr")
    long countAll();
}

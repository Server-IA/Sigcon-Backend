package com.sigcon.backend.invoices.purchase_orders.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReceiptLine;

/**
 * Repositorio JPA para lineas de recepciones de bienes.
 */
@Repository
public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, Long> {

    /**
     * Obtiene todas las lineas de una recepcion.
     *
     * @param goodsReceiptId ID de la recepcion
     * @return lista de lineas de la recepcion
     */
    List<GoodsReceiptLine> findByGoodsReceiptId(Long goodsReceiptId);

    /**
     * Obtiene las lineas de recepcion asociadas a una linea de orden de compra.
     * Se usa para calcular la cantidad total recibida de una linea OC.
     *
     * @param purchaseOrderLineId ID de la linea de OC
     * @return lista de lineas de recepcion
     */
    List<GoodsReceiptLine> findByPurchaseOrderLineId(Long purchaseOrderLineId);
}

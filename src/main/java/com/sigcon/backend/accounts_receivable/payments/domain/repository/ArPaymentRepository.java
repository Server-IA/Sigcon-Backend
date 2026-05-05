package com.sigcon.backend.accounts_receivable.payments.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.accounts_receivable.payments.domain.model.ArPayment;

/**
 * Repositorio JPA para la entidad {@link ArPayment}.
 * Provee consultas para cobros y abonos de facturas de venta.
 */
public interface ArPaymentRepository extends JpaRepository<ArPayment, Long>, JpaSpecificationExecutor<ArPayment> {

    /**
     * Verifica si ya existe un cobro con la referencia indicada (no eliminado).
     *
     * @param paymentReference referencia del cobro
     * @return true si existe un cobro con esa referencia
     */
    boolean existsByPaymentReferenceAndDeletedAtIsNull(String paymentReference);

    /**
     * Obtiene todos los cobros asociados a una factura de venta.
     *
     * @param invoiceId identificador de la factura
     * @return lista de cobros de la factura
     */
    List<ArPayment> findByInvoiceIdAndDeletedAtIsNull(Long invoiceId);

    /**
     * HU-AR-02 E3 / HU-AR-08 E3: idempotencia. Verifica si ya existe un cobro
     * con misma factura + mismo monto + misma fecha (cuando paymentReference
     * es null/blank). Evita doble-click duplicando el cobro.
     */
    boolean existsByInvoice_IdAndAmountAndPaymentDateAndDeletedAtIsNull(
            Long invoiceId, BigDecimal amount, LocalDate paymentDate);
}

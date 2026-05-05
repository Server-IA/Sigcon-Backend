package com.sigcon.backend.invoices.ap_payments.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.invoices.ap_payments.domain.model.ApPayment;

/**
 * Repositorio JPA para la entidad {@link ApPayment}.
 * Provee consultas para pagos y abonos de facturas de compra.
 */
public interface ApPaymentRepository extends JpaRepository<ApPayment, Long>, JpaSpecificationExecutor<ApPayment> {

    /**
     * Verifica si ya existe un pago con la referencia indicada (no eliminado).
     *
     * @param paymentReference referencia del pago
     * @return true si existe un pago con esa referencia
     */
    boolean existsByPaymentReferenceAndDeletedAtIsNull(String paymentReference);

    /**
     * Obtiene todos los pagos asociados a una factura especifica.
     *
     * @param invoiceId identificador de la factura
     * @return lista de pagos de la factura
     */
    List<ApPayment> findByInvoiceIdAndDeletedAtIsNull(Long invoiceId);

    /**
     * AP-08 (idempotencia): detecta un pago duplicado al mismo invoice con el mismo
     * monto y fecha. Util para evitar doble-click accidental en la UI sin depender
     * de que el usuario informe {@code paymentReference}.
     */
    boolean existsByInvoice_IdAndAmountAndPaymentDateAndDeletedAtIsNull(
            Long invoiceId, java.math.BigDecimal amount, java.time.LocalDate paymentDate);

    /**
     * HU-AP-08 (Bloque AS): pagos sin conciliar para conciliacion automatica masiva.
     * El @Filter("tenantFilter") de la entidad limita a la empresa actual.
     */
    List<ApPayment> findByBankMovementIdIsNullAndDeletedAtIsNull();
}

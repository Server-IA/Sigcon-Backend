package com.sigcon.backend.invoices.domain.events;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEvent;

/**
 * Evento de dominio publicado cuando se procesa un pago a una factura de compra.
 * Permite a otros modulos reaccionar al registro de pagos sin acoplamiento directo.
 *
 * <p>Publicado por {@code ApPaymentService.registerPayment()} despues de
 * actualizar el saldo de la factura y generar el asiento contable.</p>
 */
public class ApPaymentProcessedEvent extends ApplicationEvent {

    /** Identificador del pago registrado. */
    private final Long paymentId;

    /** Identificador de la factura a la que se aplica el pago. */
    private final Long invoiceId;

    /** Monto del pago. */
    private final BigDecimal amount;

    /** Identificador de la cuenta bancaria origen (puede ser null). */
    private final Long bankAccountId;

    /**
     * Crea un nuevo evento de pago procesado.
     *
     * @param source        objeto que origina el evento
     * @param paymentId     ID del pago
     * @param invoiceId     ID de la factura
     * @param amount        monto del pago
     * @param bankAccountId ID de la cuenta bancaria origen (puede ser null)
     */
    public ApPaymentProcessedEvent(Object source, Long paymentId, Long invoiceId,
                                    BigDecimal amount, Long bankAccountId) {
        super(source);
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.bankAccountId = bankAccountId;
    }

    /** @return ID del pago */
    public Long getPaymentId() {
        return paymentId;
    }

    /** @return ID de la factura */
    public Long getInvoiceId() {
        return invoiceId;
    }

    /** @return monto del pago */
    public BigDecimal getAmount() {
        return amount;
    }

    /** @return ID de la cuenta bancaria origen */
    public Long getBankAccountId() {
        return bankAccountId;
    }
}

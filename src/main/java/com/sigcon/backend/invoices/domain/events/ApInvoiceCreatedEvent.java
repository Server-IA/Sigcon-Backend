package com.sigcon.backend.invoices.domain.events;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEvent;

/**
 * Evento de dominio publicado cuando se crea una nueva factura de compra en el modulo AP.
 * Permite a otros modulos reaccionar a la creacion de facturas sin acoplamiento directo.
 *
 * <p>Publicado por {@code InvoiceService.createInvoice()} despues de
 * guardar la factura y generar el asiento contable.</p>
 */
public class ApInvoiceCreatedEvent extends ApplicationEvent {

    /** Identificador de la factura creada. */
    private final Long invoiceId;

    /** Monto total de la factura. */
    private final BigDecimal amount;

    /** Identificador del proveedor (tercero). */
    private final Long thirdPartyId;

    /**
     * Crea un nuevo evento de factura de compra creada.
     *
     * @param source       objeto que origina el evento
     * @param invoiceId    ID de la factura creada
     * @param amount       monto total de la factura
     * @param thirdPartyId ID del proveedor
     */
    public ApInvoiceCreatedEvent(Object source, Long invoiceId, BigDecimal amount, Long thirdPartyId) {
        super(source);
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.thirdPartyId = thirdPartyId;
    }

    /** @return ID de la factura creada */
    public Long getInvoiceId() {
        return invoiceId;
    }

    /** @return monto total de la factura */
    public BigDecimal getAmount() {
        return amount;
    }

    /** @return ID del proveedor */
    public Long getThirdPartyId() {
        return thirdPartyId;
    }
}

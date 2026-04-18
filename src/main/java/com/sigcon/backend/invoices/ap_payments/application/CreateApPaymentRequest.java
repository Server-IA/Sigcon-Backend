package com.sigcon.backend.invoices.ap_payments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para registrar un pago o abono a una factura de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateApPaymentRequest {

    /** ID de la factura a la que se aplica el pago. */
    @NotNull(message = "El ID de la factura es obligatorio")
    private Long invoiceId;

    /** Monto del pago. */
    @NotNull(message = "El monto del pago es obligatorio")
    private BigDecimal amount;

    /** Fecha del pago. */
    @NotNull(message = "La fecha del pago es obligatoria")
    private LocalDate paymentDate;

    /** Referencia del pago (transferencia, recibo, etc.). */
    private String paymentReference;

    /** Metodo de pago (TRANSFERENCIA, EFECTIVO, CHEQUE). */
    @NotBlank(message = "El metodo de pago es obligatorio")
    private String paymentMethod;

    /** ID de la cuenta bancaria origen (si aplica). */
    private Long bankAccountId;

    /** ID de la caja origen (si aplica). */
    private Long cashId;

    /** ID del cheque utilizado (si aplica). */
    private Long checkId;

    /** Observaciones adicionales. */
    private String notes;
}

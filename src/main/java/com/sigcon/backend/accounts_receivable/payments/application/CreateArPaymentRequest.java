package com.sigcon.backend.accounts_receivable.payments.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para registrar un cobro o abono a una factura de venta.
 * Cubre HUs AR-02 y AR-08.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateArPaymentRequest {

    /** ID de la factura de venta a la que se aplica el cobro. */
    @NotNull(message = "El ID de la factura es obligatorio")
    private Long invoiceId;

    /** Monto del cobro. */
    @NotNull(message = "El monto del cobro es obligatorio")
    private BigDecimal amount;

    /** Fecha del cobro. */
    @NotNull(message = "La fecha del cobro es obligatoria")
    private LocalDate paymentDate;

    /** Referencia del cobro (transferencia, recibo, etc.). */
    private String paymentReference;

    /** Metodo de pago (TRANSFERENCIA, EFECTIVO, CHEQUE). */
    @NotBlank(message = "El metodo de pago es obligatorio")
    private String paymentMethod;

    /** ID de la cuenta bancaria destino (si aplica). */
    private Long bankAccountId;

    /** ID de la caja destino (si aplica). */
    private Long cashId;

    /** ID del movimiento bancario que origino el cobro. */
    private Long bankMovementId;

    // QA CXC Bug 2 (2026-06-03 / IEEE AR-RF-02): las notas del cobro son
    // opcionales y admiten maximo 500 caracteres.
    /** Observaciones adicionales (opcional, maximo 500 caracteres). */
    @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
    private String notes;
}

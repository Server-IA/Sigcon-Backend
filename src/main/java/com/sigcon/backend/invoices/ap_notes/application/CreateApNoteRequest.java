package com.sigcon.backend.invoices.ap_notes.application;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear una nota credito o debito asociada a una factura de compra.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateApNoteRequest {

    /** ID de la factura a la que se asocia la nota. */
    @NotNull(message = "El ID de la factura es obligatorio")
    private Long invoiceId;

    /** Tipo de nota: CREDIT o DEBIT. */
    @NotBlank(message = "El tipo de nota es obligatorio (CREDIT o DEBIT)")
    private String noteType;

    /** Valor monetario de la nota. */
    @NotNull(message = "El monto de la nota es obligatorio")
    private BigDecimal amount;

    /** Razon o justificacion de la nota. */
    @NotBlank(message = "La razon de la nota es obligatoria")
    private String reason;
}

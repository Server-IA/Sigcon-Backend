package com.sigcon.backend.invoices.purchase_orders.application;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QA-BLOQUE-AY HU-AP-19 E1 (2026-05-06): request para vincular UNA factura
 * de compra a MULTIPLES recepciones (cuando una OC se recibio en partes y la
 * factura abarca varias recepciones).
 *
 * <p>Antes el endpoint {@code /receipts/{id}/link-invoice} solo aceptaba
 * vincular 1 factura a 1 recepcion, lo que rompia el three-way match cuando
 * el contador recibia una unica factura por todo el lote (entregado en
 * varios despachos).
 *
 * <p>Validaciones del service:
 * <ul>
 *   <li>Todas las recepciones deben pertenecer a la misma OC.</li>
 *   <li>Ninguna debe estar ya vinculada a otra factura (E4).</li>
 *   <li>El proveedor de la factura debe coincidir con el de las recepciones.</li>
 *   <li>El monto de la factura debe ser &le; suma de los montos recibidos
 *       (E3 bloqueo). Si es &lt; emite warning informativo (E5).</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Vincular una factura a multiples recepciones de la misma OC")
public class LinkInvoiceMultipleRequest {

    @NotNull(message = "El ID de la factura es obligatorio")
    @Schema(description = "ID de la factura de compra a vincular")
    private Long invoiceId;

    @NotEmpty(message = "Debe especificar al menos una recepcion")
    @Schema(description = "IDs de las recepciones a vincular a la factura")
    private List<Long> receiptIds;
}

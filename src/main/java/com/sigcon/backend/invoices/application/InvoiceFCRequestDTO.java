package com.sigcon.backend.invoices.application;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class InvoiceFCRequestDTO {

    @Schema(description = "ID del estado de la factura", example = "1 - En proceso, 2 - Finalizada, 3 - Cancelada")
    private Long stateInvoiceId;

    @Schema(description = "ID de la forma de pago", example = "1 - Contado, 2 - Crédito")
    @NotNull(message = "La forma de pago es requerida")
    private Long paymentFormId;

    @Schema (description = "ID del tercero", example = "1")
    @NotNull(message = "El tercero es requerido")
    private Long thirdPartyId;

    @Schema (description = "Resolución de la factura FC", example = "1234567890")
    @NotNull(message = "La resolución de la factura es requerida")
    private String resolutionInvoice;

    @Schema (description = "Fecha de compra", example = "2026-01-01")
    @NotNull(message = "La fecha de compra es requerida")
    private LocalDate invoiceDate;

    @Schema (description = "Día de vencimiento", example = "15")
    @NotNull(message = "El día de vencimiento es requerido")
    private Integer invoiceDueDay;

    @Schema(description = "Numero de factura del proveedor", example = "FAC-001234")
    private String supplierInvoiceNumber;

    @Schema(description = "Notas", example = "Notas de la factura")
    private String notes;

    @Schema (description = "Lista de items de la factura", example = "[]")
    private List<LineInvoiceRequestDTO> lineInvoices;

    /**
     * HU-AP-02 E3: version optimista. El cliente recibe `version` al cargar
     * la factura y lo envia en el PUT. Si la version en BD difiere (otro
     * usuario guardo en el medio), Hibernate lanza OptimisticLockException
     * traducida a HTTP 409 con mensaje legible. Opcional para retro-compat.
     */
    @Schema(description = "Version cargada por el cliente (HU-AP-02 E3)", example = "0")
    private Long version;
}

package com.sigcon.backend.invoices.attachments.application;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AP-13: DTO de respuesta para un documento soporte adjunto a factura de compra.
 * No incluye el contenido binario; se usa el endpoint de descarga para obtenerlo.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceAttachmentDTO {

    private Long id;
    private Long invoiceId;
    private String documentType;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String description;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}

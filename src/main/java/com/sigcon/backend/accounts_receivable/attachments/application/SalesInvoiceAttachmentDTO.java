package com.sigcon.backend.accounts_receivable.attachments.application;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para un adjunto de factura de venta.
 * No incluye el contenido binario; se usa el endpoint de descarga para obtenerlo.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SalesInvoiceAttachmentDTO {

    private Long id;
    private Long salesInvoiceId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}

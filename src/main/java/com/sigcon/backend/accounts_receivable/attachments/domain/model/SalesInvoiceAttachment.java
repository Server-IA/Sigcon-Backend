package com.sigcon.backend.accounts_receivable.attachments.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad que representa un comprobante (PDF/JPG/PNG) adjunto a una factura de venta.
 * Cubre HU AR-03: permite a usuarios contables adjuntar soportes a facturas FV.
 */
@Entity
@Table(name = "sales_invoice_attachments")
@SQLDelete(sql = "UPDATE sales_invoice_attachments SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SalesInvoiceAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador de la factura de venta propietaria del adjunto. */
    @Column(name = "sales_invoice_id", nullable = false)
    private Long salesInvoiceId;

    /** Nombre original del archivo cargado. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** MIME type del archivo (application/pdf, image/jpeg, image/png). */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** Tamaño del archivo en bytes. */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** Contenido binario del archivo (BYTEA en PostgreSQL). */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_content", nullable = false, columnDefinition = "BYTEA")
    private byte[] fileContent;

    /** Usuario que realizo la carga. */
    @Column(name = "uploaded_by", length = 150)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime uploadedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

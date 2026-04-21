package com.sigcon.backend.invoices.attachments.domain.model;

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
 * AP-13: Entidad que representa un documento soporte (OC, acta de recepcion,
 * contrato, etc.) adjunto a una factura de compra.
 *
 * <p>Permite a usuarios contables adjuntar PDFs/imagenes como respaldo legal
 * de la factura. Se clasifican por {@code documentType} para facilitar la
 * consulta: PURCHASE_ORDER, RECEPTION_ACT, CONTRACT, OTHER.
 *
 * <p>Este patron es paralelo al {@code SalesInvoiceAttachment} del modulo AR.
 */
@Entity
@Table(name = "invoice_attachments")
@SQLDelete(sql = "UPDATE invoice_attachments SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** ID de la factura de compra propietaria del adjunto. */
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    /**
     * Tipo de documento soporte.
     * Valores sugeridos: PURCHASE_ORDER, RECEPTION_ACT, CONTRACT, OTHER.
     */
    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    /** Nombre original del archivo cargado. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** MIME type del archivo. */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** Tamaño del archivo en bytes. */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** Contenido binario del archivo. */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_content", nullable = false, columnDefinition = "BYTEA")
    private byte[] fileContent;

    /** Descripcion opcional del documento (ej. numero OC, fecha acta, etc). */
    @Column(name = "description", length = 500)
    private String description;

    /** Usuario que realizo la carga. */
    @Column(name = "uploaded_by", length = 150)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime uploadedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @jakarta.persistence.PrePersist
    protected void __onCreateTenant() {
        if (this.companyId == null) this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
    }

    @jakarta.persistence.PostLoad
    protected void __onLoadTenant() {
        if (com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin()) return;
        Long current = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (current == null || this.companyId == null) return;
        if (!current.equals(this.companyId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                    "Recurso fuera del tenant actual");
        }
    }
}

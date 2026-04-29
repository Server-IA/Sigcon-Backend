package com.sigcon.backend.general.accounting.journal.attachments.domain.model;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HU-CG-05A/B/C: comprobante (PDF/JPG/PNG) adjunto a un asiento contable
 * (JournalEntry). Permite al contador soportar el comprobante con factura,
 * recibo, contrato o cualquier evidencia documental.
 *
 * <p>Multi-tenant via @Filter("tenantFilter") + @PrePersist auto-injecting
 * companyId. Soft-delete preserva auditoria contable.</p>
 */
@Entity
@Table(name = "journal_entry_supports")
@SQLDelete(sql = "UPDATE journal_entry_supports SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JournalEntrySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Multi-tenant. Auto-inyectado en @PrePersist. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Identificador del comprobante contable propietario del adjunto. */
    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    /** Nombre original del archivo cargado. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** MIME type del archivo (application/pdf, image/jpeg, image/png). */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /** Tamaño del archivo en bytes. */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * Contenido binario (BYTEA en PostgreSQL).
     *
     * <p>Se omite {@code @Lob} a proposito: Hibernate 6 lo mapea a OID por
     * default, lo cual choca con la columna {@code BYTEA} de la migracion
     * (Postgres rechaza con "bigint -> bytea"). Sin @Lob el binding usa
     * {@code SqlTypes.VARBINARY} y funciona contra bytea.</p>
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_content", nullable = false, columnDefinition = "BYTEA")
    private byte[] fileContent;

    /**
     * HU-CG-05A: tipo de soporte para clasificar el documento (FACTURA,
     * RECIBO, CONTRATO, OTRO). String libre con max 50 chars; permite que
     * el contador defina sus propias categorias por convencion.
     */
    @Column(name = "support_type", length = 50)
    private String supportType;

    /** Descripcion libre opcional del adjunto. */
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
        if (this.companyId == null) {
            this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        }
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

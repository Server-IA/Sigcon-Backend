package com.sigcon.backend.banks.archivos_soporte.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * BNK-HU-062 / BNK-HU-063: soporte conservado (extracto bancario, CSV de
 * movimientos, informe de conciliación) con hash SHA-256 inalterable y
 * retención de 10 años.
 *
 * <p>Multi-tenant (@Filter). El borrado físico antes de {@code retenerHasta}
 * se bloquea en el servicio (E5). La replicación a medio alterno (E2/E3) es
 * infraestructura no disponible en el stack local; se modela el estado pero no
 * se ejecuta la copia.
 */
@Entity
@Table(name = "archivos_soporte")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchivoSoporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Multi-tenant. Auto-inyectado en @PrePersist. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** EXTRACTO_BANCARIO | CSV_MOVIMIENTOS | INFORME_CONCILIACION | OTRO */
    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    /** Bytes originales. Sin @Lob: Hibernate 6 mapearía byte[] @Lob a OID y choca con BYTEA. */
    @Column(name = "file_content", nullable = false, columnDefinition = "BYTEA")
    private byte[] fileContent;

    /** BNK-HU-062 E1: hash SHA-256 inalterable del archivo completo. */
    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Column(name = "file_size", nullable = false)
    @Builder.Default
    private Long fileSize = 0L;

    @Column(name = "bank_account_id")
    private Long bankAccountId;

    @Column(name = "reconciliation_session_id")
    private Long reconciliationSessionId;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /** BNK-HU-063 E1: fecha_carga + 10 años. */
    @Column(name = "retener_hasta")
    private LocalDateTime retenerHasta;

    /** PENDING | OK | FAILED (E2/E3: replicación = infra). */
    @Column(name = "replication_status", nullable = false, length = 20)
    @Builder.Default
    private String replicationStatus = "PENDING";

    @Column(name = "replicated_at")
    private LocalDateTime replicatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void prePersist() {
        if (this.companyId == null) {
            this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        }
        if (this.uploadedAt == null) this.uploadedAt = LocalDateTime.now();
        // BNK-HU-063 E1: retención por defecto = carga + 10 años.
        if (this.retenerHasta == null) this.retenerHasta = this.uploadedAt.plusYears(10);
        if (this.fileSize == null) this.fileSize = this.fileContent != null ? (long) this.fileContent.length : 0L;
        if (this.replicationStatus == null) this.replicationStatus = "PENDING";
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

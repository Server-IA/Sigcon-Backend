package com.sigcon.backend.integration.domain.model;

import com.sigcon.backend.integration.domain.model.enums.DocumentType;
import com.sigcon.backend.integration.domain.model.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Tracking por documento individual dentro de un lote AAEF.
 *
 * <p>Cada {@link IntegrationBatch} contiene multiples documentos (invoices,
 * transactions). Por cada documento se crea un registro en esta tabla
 * para rastrear su procesamiento individual, el {@code accountingEntryId} del
 * JE generado y los eventuales errores de validacion o mapeo.
 *
 * <p>El campo {@code accountingEntryId} referencia logicamente a
 * {@code journal_entries.id}. No se usa FK estricta para permitir procesamiento
 * asincrono y evitar bloqueos.
 *
 * <p>Tabla: {@code af_accounting_transfers} (creada en V32 como
 * {@code integration_transfers}, renombrada en V9-ZZL para alinearse con la
 * spec AAEF v1.0 Bloque W).
 *
 * @see IntegrationBatch
 * @see com.sigcon.backend.integration.domain.model.enums.TransferStatus
 */
@Entity
@Table(name = "af_accounting_transfers")
@SQLDelete(sql = "UPDATE af_accounting_transfers SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Lote AAEF al que pertenece este documento. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private IntegrationBatch batch;

    /** ID externo del documento segun AgroFusion (ej: "INV-DISRIEGO-2026-000125"). */
    @Column(name = "document_id", nullable = false, length = 100)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "transfer_status", nullable = false, length = 30)
    private TransferStatus transferStatus = TransferStatus.PENDING;

    /**
     * ID del JE generado en {@code journal_entries.id}. Null si aun no se proceso
     * o si el documento fallo.
     */
    @Column(name = "accounting_entry_id")
    private Long accountingEntryId;

    /** Codigo de error estructurado (ej: INVALID_STATUS, AMOUNT_MISMATCH). */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** Indica si el documento puede reintentarse. False para errores irrecuperables. */
    @Builder.Default
    @Column(name = "retry_allowed", nullable = false)
    private Boolean retryAllowed = true;

    /** True si este transfer corresponde a un update Pull+Diff (RF-INT-14). */
    @Builder.Default
    @Column(name = "is_update", nullable = false)
    private Boolean isUpdate = false;

    /**
     * RF-INT-14: cuando este transfer fue generado por un Pull+Diff, apunta al
     * ExchangeId del lote INICIAL padre (OriginalExchangeId del envelope).
     * Nulo para transfers del flujo normal (lote inicial).
     * Útil para trazabilidad y reportes cross-update.
     */
    @Column(name = "original_exchange_id", length = 100)
    private String originalExchangeId;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @jakarta.persistence.PrePersist
    protected void __onCreateTenant() {
        if (this.companyId == null) {
            if (this.batch != null && this.batch.getCompanyId() != null) {
                this.companyId = this.batch.getCompanyId();
            } else {
                this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
            }
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

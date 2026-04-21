package com.sigcon.backend.integration.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * HU-INT-RF-15 E4: historial inmutable (append-only) de cada intento de
 * procesamiento de un {@link IntegrationTransfer}.
 *
 * <p>Cada vez que un transfer se procesa (inicialmente o por retry manual desde
 * la UI), se registra una fila aqui con el resultado, el error si fallo, quien
 * lo gatillo, cuando, y la nota del usuario si aplica. Esto permite al admin
 * contable ver el ciclo de vida completo del transfer (HU-INT-RF-15 E4).
 *
 * <p>NO tiene {@code @SQLDelete} ni {@code @Where} porque es append-only:
 * el frontend nunca permite borrar y el servicio solo INSERTA. Sirve como
 * evidencia auditable.
 *
 * <p>Tabla: {@code integration_transfer_history} (creada en V9-D).
 *
 * @see IntegrationTransfer
 */
@Entity
@Table(name = "integration_transfer_history")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationTransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    /** Transfer al que pertenece este intento. */
    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    /**
     * 0 = intento inicial (cuando se proceso el batch original);
     * 1+ = retries (manuales desde UI o automaticos por scheduler).
     */
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    /** SUCCESS / FAILED / RETRYING / SKIPPED. */
    @Column(name = "result_status", nullable = false, length = 30)
    private String resultStatus;

    /** Codigo de error estructurado (replica del transfer en ese momento). */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** ID del JE generado si fue exitoso. */
    @Column(name = "accounting_entry_id")
    private Long accountingEntryId;

    /** SYSTEM (procesamiento automatico) o MANUAL (retry desde UI). */
    @Column(name = "trigger_source", nullable = false, length = 20)
    @Builder.Default
    private String triggerSource = "SYSTEM";

    /** 'system' para automatico, username para retry manual. */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    /** Nota libre del usuario al hacer retry manual (max 500 chars). */
    @Column(name = "user_note", length = 500)
    private String userNote;

    /** Si se origino un nuevo batch sintetico (caso retry), su ID. */
    @Column(name = "new_batch_id")
    private Long newBatchId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

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

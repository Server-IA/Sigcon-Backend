package com.sigcon.backend.integration.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Clave de idempotencia para lotes AAEF.
 *
 * <p>Complementa el control de unicidad de {@link IntegrationBatch} (HU-INT-RF-03).
 * Cada combinacion unica de {@code (exchange_id, standard_version)} queda registrada
 * con un contador de intentos. Si AgroFusion reenvia el mismo lote por error de red,
 * SIGCON detecta el duplicado y responde HTTP 409 Conflict.
 *
 * <p>Diferencia respecto a {@link IntegrationBatch}:
 * <ul>
 *   <li>{@code IntegrationBatch} almacena el payload y estado del lote.</li>
 *   <li>{@code IntegrationIdempotencyKey} rastrea los intentos de recepcion (incluido
 *       el primer intento que pudo haber fallado antes de persistir el batch).</li>
 * </ul>
 *
 * <p>Tabla: {@code integration_idempotency_keys} (V32).
 */
@Entity
@Table(name = "integration_idempotency_keys")
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationIdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /** Multi-tenant (V10-C). Auto-inyectado en @PrePersist. */
    @jakarta.persistence.Column(name = "company_id", nullable = false)
    private Long companyId;
    @Column(name = "exchange_id", nullable = false, length = 64)
    private String exchangeId;

    @Column(name = "standard_version", nullable = false, length = 10)
    private String standardVersion;

    /** Referencia al batch si se proceso exitosamente (null si fallo la persistencia). */
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "first_received_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime firstReceivedAt;

    @Column(name = "last_attempt_at", nullable = false)
    private LocalDateTime lastAttemptAt;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

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

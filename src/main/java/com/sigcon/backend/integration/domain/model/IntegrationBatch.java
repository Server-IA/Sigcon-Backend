package com.sigcon.backend.integration.domain.model;

import com.sigcon.backend.integration.domain.model.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lote AAEF recibido de AgroFusion.
 *
 * <p>Representa la cabecera de un payload JSON AAEF (RF-INT-13) recibido por el
 * endpoint {@code POST /api/contabilidad/aaef}. Contiene la metadata del lote,
 * el summary de totales, el payload JSON original y el estado del procesamiento.
 *
 * <p>La unicidad del lote se garantiza por la clave compuesta {@code (exchange_id,
 * standard_version)} (HU-INT-RF-03 - Idempotencia).
 *
 * <p>Tabla: {@code integration_batches} (creada en migracion V32).
 *
 * @see com.sigcon.backend.integration.domain.model.enums.BatchStatus
 * @see IntegrationTransfer
 */
@Entity
@Table(name = "integration_batches")
@SQLDelete(sql = "UPDATE integration_batches SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---------- Metadata del lote (RF-INT-13 metadata) ----------

    /** ID unico del lote emitido por AgroFusion. Formato: AF-YYYY-MM-NNNNN. */
    @Column(name = "exchange_id", nullable = false, length = 64)
    private String exchangeId;

    /** Version del estandar AAEF. Ejemplo: "1.0". */
    @Column(name = "standard_version", nullable = false, length = 10)
    private String standardVersion;

    /** Identificador del sistema origen que produjo el lote (ej: "disriego-prod-01"). */
    @Column(name = "source_system_id", length = 100)
    private String sourceSystemId;

    /** Nombre legible del sistema origen (ej: "Disriego"). */
    @Column(name = "source_system_name", length = 200)
    private String sourceSystemName;

    /** NIT de la empresa origen. */
    @Column(name = "source_system_nit", length = 20)
    private String sourceSystemNit;

    /** Ambiente del sistema origen: production | staging | development. */
    @Column(name = "environment", length = 20)
    private String environment;

    /** Usuario o servicio que genero el lote en AgroFusion. */
    @Column(name = "generated_by", length = 200)
    private String generatedBy;

    /** Inicio del periodo contable consultado. */
    @Column(name = "period_from")
    private LocalDate periodFrom;

    /** Fin del periodo contable consultado. */
    @Column(name = "period_to")
    private LocalDate periodTo;

    // ---------- Summary del lote (RF-INT-13 summary) ----------

    @Builder.Default
    @Column(name = "total_documents", nullable = false)
    private Integer totalDocuments = 0;

    @Builder.Default
    @Column(name = "total_invoices", nullable = false)
    private Integer totalInvoices = 0;

    @Builder.Default
    @Column(name = "total_transactions", nullable = false)
    private Integer totalTransactions = 0;

    // Nota: la columna total_payroll existe en BD (V32) por compatibilidad
    // historica pero el bloque payroll del estandar AAEF fue desestimado del
    // alcance del proyecto. Hibernate ignora la columna al no tener mapping.

    @Column(name = "total_gross_amount", precision = 20, scale = 2)
    private BigDecimal totalGrossAmount;

    @Column(name = "total_taxes", precision = 20, scale = 2)
    private BigDecimal totalTaxes;

    @Column(name = "total_net", precision = 20, scale = 2)
    private BigDecimal totalNet;

    /** Moneda ISO 4217. Ejemplo: "COP". */
    @Column(name = "currency", length = 3)
    private String currency;

    // ---------- Estado y procesamiento ----------

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private BatchStatus status = BatchStatus.RECEIVED;

    /** Payload JSON original recibido, conservado para auditoria y reintentos. */
    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    // V9-P: columnas TIMESTAMPTZ (guardan instante absoluto, cliente elige TZ al mostrar).
    // Hibernate respeta columnDefinition y NO intenta revertirlo en ddl-auto=update.
    @Column(name = "received_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    @CreationTimestamp
    private LocalDateTime receivedAt;

    @Column(name = "processed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime processedAt;

    @Column(name = "ack_sent_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime ackSentAt;

    @Builder.Default
    @Column(name = "ack_retry_count", nullable = false)
    private Integer ackRetryCount = 0;

    /**
     * HU-INT-RF-13: instante en el que el {@code AckRetryScheduler} debera
     * intentar reenviar el ACK. Calculado con backoff exponencial
     * (1 min, 2 min, 4 min) desde el ultimo fallo. Null si no hay reintento
     * pendiente o el ACK ya fue exitoso.
     */
    @Column(name = "ack_next_retry_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime ackNextRetryAt;

    /** Mensaje de error de alto nivel (si el lote fallo completo). */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ---------- Auditoria ----------

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime deletedAt;
}

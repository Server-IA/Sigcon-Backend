package com.sigcon.backend.accounts_receivable.dian.submissions.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro de envio de una factura electronica al proveedor tecnologico (PSE) / DIAN.
 * Guarda el XML UBL 2.1, el CUFE, el estado y la respuesta.
 */
@Entity
@Table(name = "dian_invoice_submissions")
@SQLDelete(sql = "UPDATE dian_invoice_submissions SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DianInvoiceSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_invoice_id", nullable = false)
    private Long salesInvoiceId;

    @Column(name = "cufe", length = 200)
    private String cufe;

    @Column(name = "xml_content", columnDefinition = "TEXT")
    private String xmlContent;

    @Column(name = "xml_base64", columnDefinition = "TEXT")
    private String xmlBase64;

    @Column(name = "submission_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DianSubmissionStatus submissionStatus = DianSubmissionStatus.PENDING;

    @Column(name = "dian_response", columnDefinition = "TEXT")
    private String dianResponse;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** Identificador de trazabilidad generado para seguimiento del envio. */
    @Column(name = "track_id", length = 60)
    private String trackId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.submissionStatus == null) this.submissionStatus = DianSubmissionStatus.PENDING;
        if (this.attemptCount == null) this.attemptCount = 0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

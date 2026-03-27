package com.sigcon.backend.banks.checks.domain.model;

import com.sigcon.backend.banks.checkbooks.domain.model.Checkbook;
import com.sigcon.backend.banks.checks.domain.model.enums.CheckStatus;
import com.sigcon.backend.banks.checks.domain.model.enums.CheckType;
import com.sigcon.backend.banks.checks.domain.model.enums.ConciliationMethod;
import com.sigcon.backend.banks.checks.domain.model.enums.IncidentType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "checks")
@SQLDelete(sql = "UPDATE checks SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Check {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "checkbooks_id")
    private Checkbook checkbook;

    @Column(name = "number_check", nullable = false)
    private Integer numberCheck;

    @Column(name = "beneficiary", nullable = false, length = 200)
    private String beneficiary;

    @Column(name = "value", nullable = false, precision = 20, scale = 2)
    private BigDecimal value;

    @Column(name = "concept", nullable = false, length = 200)
    private String concept;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_check", nullable = false, length = 16)
    @Builder.Default
    private CheckType typeCheck = CheckType.FISICO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_check", nullable = false, length = 16)
    @Builder.Default
    private CheckStatus statusCheck = CheckStatus.EMITIDO;

    @Column(name = "financial_movement_id")
    private Long financialMovementId;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @Column(name = "support_document_path", length = 500)
    private String supportDocumentPath;

    @Column(name = "support_document_mime", length = 50)
    private String supportDocumentMime;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", length = 16)
    private IncidentType incidentType;

    @Column(name = "incident_date")
    private LocalDate incidentDate;

    @Column(name = "incident_detail", columnDefinition = "TEXT")
    private String incidentDetail;

    @Column(name = "incident_actions", length = 500)
    private String incidentActions;

    @Column(name = "block_payment", nullable = false)
    @Builder.Default
    private Boolean blockPayment = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "conciliation_method", length = 16)
    private ConciliationMethod conciliationMethod;

    @Column(name = "collection_reference", length = 100)
    private String collectionReference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

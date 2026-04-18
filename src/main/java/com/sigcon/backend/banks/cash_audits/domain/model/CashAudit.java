package com.sigcon.backend.banks.cash_audits.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad JPA para arqueos de caja (BNK-RF-17 a BNK-RF-20).
 * Registra el conteo fisico del efectivo en una caja y lo compara
 * con el saldo del sistema, generando un asiento contable de ajuste
 * si existe diferencia al aprobar el arqueo.
 */
@Entity
@Table(name = "cash_audits")
@SQLDelete(sql = "UPDATE cash_audits SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_id", nullable = false)
    private Cash cash;

    @Column(name = "audit_date", nullable = false)
    private LocalDate auditDate;

    @Column(name = "system_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal systemBalance;

    @Column(name = "physical_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal physicalBalance;

    @Column(name = "difference", nullable = false, precision = 19, scale = 2)
    private BigDecimal difference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CashAuditStatus status = CashAuditStatus.ABIERTO;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "created_by")
    private Long createdBy;

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
        if (this.status == null) {
            this.status = CashAuditStatus.ABIERTO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

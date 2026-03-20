package com.sigcon.backend.checkbooks.domain.model;

import com.sigcon.backend.checkbooks.domain.model.enums.CheckbookStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "checkbooks",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_account_id", "checkbook_number"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SQLDelete(sql = "UPDATE checkbooks SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Checkbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Column(name = "checkbook_number", nullable = false, length = 30)
    private String checkbookNumber;

    @Column(name = "issuing_bank", nullable = false, length = 150)
    private String issuingBank;

    @Column(name = "check_start_number", nullable = false)
    private Long checkStartNumber;

    @Column(name = "check_end_number", nullable = false)
    private Long checkEndNumber;

    @Column(name = "total_checks", nullable = false)
    private Integer totalChecks;

    @Column(name = "used_checks", nullable = false)
    private Integer usedChecks;

    @Column(name = "available_checks", nullable = false)
    private Integer availableChecks;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "activation_date")
    private LocalDate activationDate;

    @Enumerated(EnumType.STRING)
    private CheckbookStatus status;

    @Column(name = "observations")
    private String observations;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.usedChecks == null) this.usedChecks = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
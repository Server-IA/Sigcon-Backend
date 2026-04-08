package com.sigcon.backend.banks.checkbooks.domain.model;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.checkbooks.domain.model.enums.CheckbookStatus;
import com.sigcon.backend.parametrization.companies.domain.model.Company;

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
@SQLDelete(sql = "UPDATE checkbooks SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Checkbook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    private String checkbookNumber;
    private String issuingBank;

    private Long checkStartNumber;
    private Long checkEndNumber;

    private Integer totalChecks;
    private Integer usedChecks;
    private Integer availableChecks;

    private LocalDate receivedDate;
    private LocalDate activationDate;

    @Enumerated(EnumType.STRING)
    private CheckbookStatus status;

    private String observations;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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
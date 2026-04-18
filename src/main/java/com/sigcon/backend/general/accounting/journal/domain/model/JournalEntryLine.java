package com.sigcon.backend.general.accounting.journal.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Linea de detalle de un asiento contable.
 * Cada linea registra un debito o credito a una cuenta contable especifica.
 */
@Entity
@Table(name = "journal_entry_lines")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JournalEntryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(name = "line_order", nullable = false)
    private Integer lineOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_account_id", nullable = false)
    private AccountingAccount accountingAccount;

    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "third_party_nit", length = 20)
    private String thirdPartyNit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

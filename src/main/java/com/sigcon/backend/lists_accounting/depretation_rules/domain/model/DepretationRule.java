package com.sigcon.backend.lists_accounting.depretation_rules.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


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
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "depretation_rules", uniqueConstraints = {
    @UniqueConstraint(name = "unique_name_accounting_account_effective_date", columnNames = {"name", "accounting_account_id", "effective_date", "deleted_at"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepretationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 

    @Column(nullable = false, length = 100)
    @NotBlank(message = "El nombre es obligatorio")
    private String name; 

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "El tipo de depreciación es obligatorio")
    private DepretationType depretationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_account_id", nullable = false)
    @NotNull(message = "La cuenta contable es obligatoria")
    private AccountingAccount accountingAccount;

    @Column(nullable = false, precision = 5, scale = 2)
    @NotNull(message = "La tasa de depreciación es obligatoria")
    private BigDecimal depretationRate;

    @Column(nullable = false)
    @NotNull(message = "La vida util es obligatoria")
    private Integer usefulLifeYears;

    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull(message = "El valor residual es obligatorio")
    private BigDecimal residualValue;

    @Column(nullable = false)
    @NotNull(message = "La fecha de vigencia es obligatoria")
    private LocalDate effectiveDate;

    //Descripcion estructurada como texto largo
    @Column(columnDefinition = "TEXT", nullable = false)
    @NotNull(message = "La descripción estructurada es obligatoria")
    private String descriptionStructured;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DepretationStatus status = DepretationStatus.ACTIVE; 

    // Parte de la auditoria (comnetada porque no se si esta bien)
    /* 
    @Column(nullable = false, name = "created_by_id")
    @NotNull(message = "El usuario creador es obligatorio")
    private Long createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
    private User createdBy;
    */ 

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
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

    @PreRemove
    protected void onDelete() {
        this.deletedAt = LocalDateTime.now();
    }

}

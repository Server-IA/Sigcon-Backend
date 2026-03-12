package com.sigcon.backend.lists_accounting.accounting_lists.domain.model;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "cfg_chart_of_accounts"
)
@SQLDelete(sql = "UPDATE cfg_chart_of_accounts SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartOfAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, name = "account_code")
    @NotBlank(message = "El codigo oficial de la cuenta es obligatorio")
    @Size(min = 1, max = 10, message = "El codigo oficial debe tener entre 1 y 10 caracteres")
    private String code;

    @Column(nullable = false, length = 100, name = "account_name")
    @NotBlank(message = "El nombre de la cuenta es obligatorio")
    @Size(min = 1, max = 100, message = "El nombre de la cuenta debe tener maximo 100 caracteres")
    private String name;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "La clase de la cuenta es obligatoria")
    @Column(nullable = false, length = 30, name = "account_class")
    private AccountClass accountClass;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "El nivel jerarquico de la cuenta es obligatorio")
    @Column(nullable = false, length = 20, name = "account_level")
    private AccountLevel accountLevel;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "La naturaleza de la cuenta es obligatoria")
    @Column(nullable = false, length = 20, name = "account_nature")
    private AccountNature accountNature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, name = "account_status")
    private AccountStatus status;

    @Column(nullable = false, updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(length = 255, name = "deleted_reason")
    private String deletedReason;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.deletedAt = null;
        this.deletedReason = null;
        this.status = AccountStatus.ACTIVE;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

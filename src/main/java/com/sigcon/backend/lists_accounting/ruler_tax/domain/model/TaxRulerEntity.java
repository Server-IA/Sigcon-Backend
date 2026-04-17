package com.sigcon.backend.lists_accounting.ruler_tax.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.StatusRulerTax;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.TypeRulerTax;


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
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ruler_tax")
@SQLDelete(sql = "UPDATE ruler_tax SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class TaxRulerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_account_id", nullable = false)
    private AccountingAccount accountingAccount;

    @Column(name = "name", nullable = false)
    @NotNull(message = "El nombre es requerido")
    @Size(min = 1, max = 100, message = "El nombre debe tener entre 1 y 100 caracteres")
    private String name;

    @Column(name = "type_ruler_tax", nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "El tipo de regla de impuesto es requerido")
    private TypeRulerTax typeRulerTax;

    @Column(name = "percentage", nullable = false)
    @Min(value = 0, message = "La tarifa debe ser mayor que 0")
    @Max(value = 100, message = "La tarifa debe ser menor que 100")
    @NotNull(message = "La tarifa es requerida")
    private Double percentage;

    @Column(name = "description", nullable = true)
    @Size(min = 1, max = 1000, message = "La descripción debe tener entre 1 y 1000 caracteres")
    private String description;

    @Column(name = "scope", nullable = true)
    @Size(min = 1, max = 1000, message = "El alcance debe tener entre 1 y 1000 caracteres")
    private String scope;

    /** Tope minimo en UVT para aplicar la retencion (HU v2.0 AP-06) */
    @Column(name = "min_amount_uvt")
    private Double minAmountUvt;

    /** Valor de la UVT del anio fiscal vigente */
    @Column(name = "uvt_value_year")
    private Double uvtValueYear;

    @Column(name = "start_date", nullable = false)
    private LocalDate dateStart;

    @Column(name = "end_date", nullable = true)
    private LocalDate dateEnd;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "El estado es requerido")
    private StatusRulerTax status = StatusRulerTax.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

}

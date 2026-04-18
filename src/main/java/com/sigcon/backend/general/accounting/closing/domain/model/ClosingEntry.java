package com.sigcon.backend.general.accounting.closing.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.general.accounting.closing.domain.model.enums.ClosingStatus;
import com.sigcon.backend.general.accounting.closing.domain.model.enums.ClosingType;

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
 * Entidad de cierre contable.
 * Registra cada operacion de cierre mensual, anual o apertura,
 * vinculando al asiento contable generado por el proceso.
 */
@Entity
@Table(name = "cg_closing_entries")
@SQLDelete(sql = "UPDATE cg_closing_entries SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClosingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Anio fiscal del cierre. */
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    /** Mes del cierre (1-12 para mensual, 12 para anual). */
    @Column(name = "fiscal_month", nullable = false)
    private Integer fiscalMonth;

    /** Tipo de cierre: MONTHLY, ANNUAL u OPENING. */
    @Enumerated(EnumType.STRING)
    @Column(name = "closing_type", nullable = false, length = 20)
    private ClosingType closingType;

    /** ID del asiento contable generado por el cierre. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** Estado del cierre. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ClosingStatus status = ClosingStatus.COMPLETED;

    /** Notas u observaciones del cierre. */
    @Column(name = "notes", length = 500)
    private String notes;

    /** Usuario que ejecuto el cierre. */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
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

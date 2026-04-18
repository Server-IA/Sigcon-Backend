package com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model;

import java.time.LocalDate;
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
 * Resolucion DIAN que autoriza la numeracion de facturas electronicas.
 * Incluye rango autorizado, vigencia y clave tecnica para el calculo del CUFE
 * segun la Resolucion 0042 de 2020 y el Anexo Tecnico de facturacion electronica.
 */
@Entity
@Table(name = "dian_resolutions")
@SQLDelete(sql = "UPDATE dian_resolutions SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DianResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numero oficial de la resolucion autorizada por la DIAN. */
    @Column(name = "resolution_number", nullable = false, length = 100, unique = true)
    private String resolutionNumber;

    /** Prefijo autorizado para la numeracion (por ejemplo "FV", "FE"). */
    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix;

    /** Primer numero autorizado del rango. */
    @Column(name = "start_number", nullable = false)
    private Long startNumber;

    /** Ultimo numero autorizado del rango. */
    @Column(name = "end_number", nullable = false)
    private Long endNumber;

    /** Numero actual de la numeracion (se incrementa al asignar consecutivo). */
    @Column(name = "current_number", nullable = false)
    @Builder.Default
    private Long currentNumber = 0L;

    /** Fecha de inicio de vigencia de la resolucion. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Fecha de finalizacion de vigencia de la resolucion. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Clave tecnica entregada por la DIAN para calcular el CUFE. */
    @Column(name = "technical_key", length = 200)
    private String technicalKey;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DianResolutionStatus status = DianResolutionStatus.ACTIVE;

    @Column(name = "notes", length = 1000)
    private String notes;

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
        if (this.status == null) this.status = DianResolutionStatus.ACTIVE;
        if (this.currentNumber == null) this.currentNumber = this.startNumber != null ? this.startNumber - 1 : 0L;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

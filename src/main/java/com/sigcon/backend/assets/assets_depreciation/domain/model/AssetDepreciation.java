package com.sigcon.backend.assets.assets_depreciation.domain.model;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets_depreciation.domain.model.enums.DepreciationMethod;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ACT-RF-02 — Histórico de depreciación de activos.
 *
 * <p>Registra un snapshot inmutable de cada depreciación aplicada a un activo.
 * Este registro se genera automáticamente en cada ejecución del cálculo de
 * depreciación y nunca debe ser modificado manualmente.</p>
 *
 * <p>Relación con {@link Assets}: muchas depreciaciones pueden pertenecer a un
 * solo activo (relación N:1).</p>
 */
@Entity
@Table(name = "assets_depreciation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDepreciation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Activo al que pertenece este registro histórico.
     * La relación es LAZY para evitar cargas innecesarias al paginar el historial.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Assets asset;

    /**
     * Período contable en formato YYYY-MM (ej: "2026-03").
     */
    @Column(name = "depreciation_period", nullable = false, length = 7)
    private String depreciationPeriod;

    /**
     * Valor en libros del activo ANTES de aplicar la depreciación de este período.
     */
    @Column(name = "previous_book_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal previousBookValue;

    /**
     * Valor en libros del activo DESPUÉS de aplicar la depreciación de este período.
     */
    @Column(name = "current_book_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBookValue;

    /**
     * Monto depreciado en este período (previousBookValue − currentBookValue).
     */
    @Column(name = "depreciation_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal depreciationAmount;

    /**
     * Método de depreciación aplicado en este cálculo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false, length = 40)
    private DepreciationMethod depreciationMethod;

    /**
     * Fecha en que se ejecutó el cálculo (LocalDate.now() al momento del proceso).
     */
    @Column(name = "calculation_date", nullable = false)
    private LocalDate calculationDate;

    /**
     * Timestamp de creación del registro histórico. Se asigna automáticamente.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

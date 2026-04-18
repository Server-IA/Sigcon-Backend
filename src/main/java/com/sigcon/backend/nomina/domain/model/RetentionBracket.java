package com.sigcon.backend.nomina.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HU-NOM-03 E2: tabla de retencion en la fuente parametrizable (Art. 383 ET).
 *
 * <p>Permite configurar los rangos de UVT y sus tarifas marginales aplicables
 * al ingreso laboral gravable de cada empleado, por año gravable.
 *
 * <p>Formula Art. 383 ET:
 * {@code retencion = max(0, (ingreso_uvt - uvt_offset) * marginal_rate + fixed_uvt_amount) * uvt}
 *
 * <p>Donde:
 * <ul>
 *   <li>{@code ingreso_uvt} = ingreso_gravable / UVT vigente</li>
 *   <li>{@code uvt_offset} = limite inferior del rango (en UVT)</li>
 *   <li>{@code marginal_rate} = tarifa marginal del rango (ej. 0.19 para 19%)</li>
 *   <li>{@code fixed_uvt_amount} = monto fijo en UVT acumulado de rangos anteriores</li>
 * </ul>
 *
 * <p>Append-only: el admin puede agregar rangos pero no editar los historicos
 * (auditoria tributaria). Tabla creada en V9-G.
 */
@Entity
@Table(name = "payroll_retention_brackets")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RetentionBracket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    @Column(name = "uvt_min", nullable = false, precision = 15, scale = 4)
    private BigDecimal uvtMin;

    /** Null = sin limite superior (ultimo rango). */
    @Column(name = "uvt_max", precision = 15, scale = 4)
    private BigDecimal uvtMax;

    @Column(name = "marginal_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal marginalRate;

    @Column(name = "uvt_offset", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal uvtOffset = BigDecimal.ZERO;

    @Column(name = "fixed_uvt_amount", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal fixedUvtAmount = BigDecimal.ZERO;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

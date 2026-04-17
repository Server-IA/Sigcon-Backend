package com.sigcon.backend.nomina.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HU-NOM-03: linea de concepto aplicada a un recibo.
 *
 * <p>Cada concepto que aplica al recibo (salario base, salud 4%, pension 4%,
 * rete fuente, aportes patronales, etc.) se materializa aqui como una linea
 * independiente con su monto calculado. Esto permite:
 * <ul>
 *   <li>Detallar el comprobante individual (HU-NOM-06 E1).</li>
 *   <li>Agregar totales por tipo (EARNING/DEDUCTION/EMPLOYER_CONTRIBUTION).</li>
 *   <li>Generar el JE contable usando los mappings correspondientes.</li>
 * </ul>
 */
@Entity
@Table(name = "payroll_lines")
@SQLDelete(sql = "UPDATE payroll_lines SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayrollLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_id", nullable = false)
    private Long receiptId;

    @Column(name = "concept_code", nullable = false, length = 50)
    private String conceptCode;

    @Column(name = "concept_name", nullable = false, length = 200)
    private String conceptName;

    /** EARNING | DEDUCTION | EMPLOYER_CONTRIBUTION. */
    @Column(name = "line_type", nullable = false, length = 30)
    private String lineType;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "line_order", nullable = false)
    @Builder.Default
    private Integer lineOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

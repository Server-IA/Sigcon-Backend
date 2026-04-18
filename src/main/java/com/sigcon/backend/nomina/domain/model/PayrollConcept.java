package com.sigcon.backend.nomina.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * HU-NOM-02: concepto de nomina (devengado, deduccion o aporte patronal).
 *
 * <p>Cada concepto describe COMO se calcula (formula via porcentaje + base o
 * expresion libre) y las cuentas PUC donde impactara el JE generado por la
 * liquidacion (HU-NOM-03).
 *
 * <p>Los 17 conceptos legales colombianos se cargan en la migracion V9-G
 * (HU-NOM-02 E2): salud/pension 4% empleado + 8.5%/12% empresa, SENA 2%,
 * ICBF 3%, caja 4%, cesantias 8.33%, prima 8.33%, vacaciones 4.17%, etc.
 */
@Entity
@Table(name = "payroll_concepts")
@SQLDelete(sql = "UPDATE payroll_concepts SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayrollConcept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codigo unico (ej: SALUD_EMPLEADO, CESANTIAS). */
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** EARNING | DEDUCTION | EMPLOYER_CONTRIBUTION. */
    @Column(name = "concept_type", nullable = false, length = 30)
    private String conceptType;

    /** SALARY | IBC | FIXED | CUSTOM. Base sobre la que aplica el porcentaje. */
    @Column(name = "base_calculation", length = 30)
    private String baseCalculation;

    /** Porcentaje a aplicar sobre la base (ej: 4.00 para 4%). */
    @Column(precision = 10, scale = 4)
    private BigDecimal percentage;

    /** Monto fijo si no aplica porcentaje (ej: auxilio de transporte). */
    @Column(name = "fixed_amount", precision = 20, scale = 2)
    private BigDecimal fixedAmount;

    /** Expresion opcional si el calculo es CUSTOM (futuro - motor de formulas). */
    @Column(name = "formula_expression", columnDefinition = "TEXT")
    private String formulaExpression;

    /** FK a accounting_accounts para el debito del JE. */
    @Column(name = "accounting_account_debit_id")
    private Long accountingAccountDebitId;

    /** FK a accounting_accounts para el credito del JE. */
    @Column(name = "accounting_account_credit_id")
    private Long accountingAccountCreditId;

    /** Referencia legal (ej: "CST Art. 127", "Ley 100/1993 Art. 204"). */
    @Column(name = "legal_reference", length = 100)
    private String legalReference;

    /** ACTIVE | INACTIVE. Los inactivos no se aplican en liquidaciones. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

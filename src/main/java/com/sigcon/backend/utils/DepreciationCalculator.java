package com.sigcon.backend.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Clase utilitaria con métodos estáticos puros para el cálculo matemático de
 * depreciación.
 * <p>
 * Esta clase NO tiene dependencias de Spring ni efectos secundarios.
 * El servicio de negocio ({@code DepreciationCalculationService}) es el único
 * responsable
 * de validar reglas de negocio antes de invocar estos métodos.
 * </p>
 *
 * <b>ACT-RF-02 — Cálculo Automático de Depreciación</b>
 */
public final class DepreciationCalculator {

    private DepreciationCalculator() {
        // Clase de utilidad — no instanciable
    }

    /**
     * Método de línea recta (STRAIGHT_LINE / LINEAR).
     * <p>
     * Fórmula: {@code (valorAdquisición - valorResidual) / vidaÚtilMeses}
     * </p>
     *
     * @param acquisitionValue valor original de adquisición del activo
     * @param residualValue    valor residual al final de la vida útil (de la regla
     *                         de depreciación)
     * @param usefulLifeMonths vida útil en meses
     * @return depreciación mensual redondeada a 2 decimales
     * @throws IllegalArgumentException si la vida útil es 0 o negativa
     */
    public static BigDecimal calculateStraightLine(
            BigDecimal acquisitionValue,
            BigDecimal residualValue,
            int usefulLifeMonths) {

        if (usefulLifeMonths <= 0) {
            throw new IllegalArgumentException("Vida útil no definida");
        }
        if (acquisitionValue == null) {
            throw new IllegalArgumentException("El valor de adquisición es requerido");
        }
        BigDecimal safeResidual = residualValue != null ? residualValue : BigDecimal.ZERO;

        return acquisitionValue
                .subtract(safeResidual)
                .divide(BigDecimal.valueOf(usefulLifeMonths), 2, RoundingMode.HALF_UP);
    }

    /**
     * Método de saldo decreciente (DECLINING_BALANCE / DECREASING).
     * <p>
     * Aplica la tasa anual de la regla de depreciación convertida a mensual.
     * Fórmula: {@code valorLibros × (tasaAnual / 1200)}
     * </p>
     *
     * @param currentBookValue valor neto en libros actual del activo
     * @param annualRate       tasa anual de depreciación en porcentaje (ej: 20 =>
     *                         20%)
     *                         proveniente de
     *                         {@code DepretationRule.depretationRate}
     * @return depreciación mensual redondeada a 2 decimales
     * @throws IllegalArgumentException si la tasa o el valor en libros son
     *                                  inválidos
     */
    public static BigDecimal calculateDecliningBalance(
            BigDecimal currentBookValue,
            BigDecimal annualRate) {

        if (currentBookValue == null || currentBookValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor en libros del activo no es válido para saldo decreciente");
        }
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Tasa de depreciación inválida");
        }

        // Convertir tasa anual a mensual: tasaAnual / 12 / 100
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);

        return currentBookValue
                .multiply(monthlyRate)
                .setScale(2, RoundingMode.HALF_UP);
    }
}

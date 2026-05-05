package com.sigcon.backend.accounts_receivable.sales_invoices.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerEntity;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.TypeRulerTax;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.repository.RuleTaxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AR-13: Motor central tributario para facturas de venta.
 * Calcula IVA (TAX) y retenciones (WITHHOLDING) a partir de reglas
 * {@link TaxRulerEntity}, aplicando validacion de tope UVT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SalesTaxEngine {

    private final RuleTaxRepository ruleTaxRepository;

    /**
     * Resultado del calculo tributario sobre una base gravable.
     */
    public static class TaxCalculationResult {
        public final BigDecimal tax;
        public final BigDecimal withholding;

        public TaxCalculationResult(BigDecimal tax, BigDecimal withholding) {
            this.tax = tax != null ? tax : BigDecimal.ZERO;
            this.withholding = withholding != null ? withholding : BigDecimal.ZERO;
        }
    }

    /**
     * AR-04 + AR-13: calcula IVA y retenciones sobre una base gravable
     * segun las reglas indicadas.
     *
     * @param base       base gravable (cantidad * precio - descuento)
     * @param taxRuleIds IDs de reglas tributarias a aplicar
     * @return resultado con total de impuesto y total de retencion
     */
    public TaxCalculationResult calculate(BigDecimal base, List<Long> taxRuleIds) {
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalWithholding = BigDecimal.ZERO;

        if (base == null) base = BigDecimal.ZERO;
        if (taxRuleIds == null || taxRuleIds.isEmpty()) {
            return new TaxCalculationResult(totalTax, totalWithholding);
        }

        for (Long ruleId : taxRuleIds) {
            TaxRulerEntity rule = ruleTaxRepository.findById(ruleId).orElse(null);
            if (rule == null) {
                // HU-AR-13 E2: si falta una regla configurada, NO se silencia.
                // Lanza error claro indicando la regla faltante para que el contador
                // la configure antes de continuar.
                log.error("Regla tributaria id={} no encontrada en cfg_ruler_tax", ruleId);
                throw new IllegalStateException(
                        "Falta una regla tributaria configurada (id=" + ruleId
                        + "). Verifique las reglas en Listas Contables antes de calcular impuestos.");
            }

            BigDecimal percentage = rule.getPercentage() != null
                    ? BigDecimal.valueOf(rule.getPercentage())
                    : BigDecimal.ZERO;

            if (rule.getTypeRulerTax() == TypeRulerTax.TAX) {
                // IVA: base * porcentaje / 100
                BigDecimal amount = base.multiply(percentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                totalTax = totalTax.add(amount);
            } else if (rule.getTypeRulerTax() == TypeRulerTax.WITHHOLDING) {
                // AR-04: validar tope UVT. Si base < min_amount_uvt * uvt_value_year
                // la retencion no se aplica (HU AP-06 replicada para ventas).
                if (rule.getMinAmountUvt() != null && rule.getUvtValueYear() != null) {
                    BigDecimal tope = BigDecimal.valueOf(
                            rule.getMinAmountUvt() * rule.getUvtValueYear());
                    if (base.compareTo(tope) < 0) {
                        log.info("Retencion regla {} omitida: base {} < tope UVT {}",
                                rule.getId(), base, tope);
                        continue;
                    }
                }
                BigDecimal amount = base.multiply(percentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                totalWithholding = totalWithholding.add(amount);
            }
        }

        return new TaxCalculationResult(totalTax, totalWithholding);
    }
}

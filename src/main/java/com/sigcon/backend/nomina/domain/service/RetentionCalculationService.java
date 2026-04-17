package com.sigcon.backend.nomina.domain.service;

import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import com.sigcon.backend.nomina.domain.model.RetentionBracket;
import com.sigcon.backend.nomina.domain.repository.RetentionBracketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * HU-NOM-03 E2: motor de retencion en la fuente sobre salarios (Art. 383 ET).
 *
 * <p>Usa la tabla parametrizable {@code payroll_retention_brackets} por año
 * gravable + el valor de la UVT leido del parametro {@code sigcon.nomina.uvt}.
 *
 * <p>Si no hay brackets definidos para el año, retorna 0 (se considera que
 * no hay obligacion de retener hasta que el admin cargue los rangos).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionCalculationService {

    private final RetentionBracketRepository bracketRepository;
    private final ParameterRepository parameterRepository;

    private static final String PARAM_UVT = "sigcon.nomina.uvt";
    private static final BigDecimal DEFAULT_UVT = new BigDecimal("47065");

    /**
     * Calcula la retencion en la fuente aplicable sobre un ingreso laboral
     * gravable (ya con las depuraciones permitidas por el Art. 387 ET aplicadas
     * por el llamador).
     *
     * @param taxYear                 año gravable para consultar los rangos
     * @param grossTaxableIncomeCOP   ingreso laboral gravable en pesos
     * @return retencion en pesos (ya multiplicada por UVT), nunca negativa
     */
    public BigDecimal calculate(int taxYear, BigDecimal grossTaxableIncomeCOP) {
        if (grossTaxableIncomeCOP == null
                || grossTaxableIncomeCOP.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal uvt = currentUvt();
        BigDecimal incomeInUvt = grossTaxableIncomeCOP.divide(uvt, 4, RoundingMode.HALF_UP);

        List<RetentionBracket> brackets =
                bracketRepository.findByTaxYearAndDeletedAtIsNullOrderByUvtMinAsc(taxYear);
        if (brackets.isEmpty()) {
            log.warn("No hay brackets de retencion fuente cargados para el año {}. Retornando 0.", taxYear);
            return BigDecimal.ZERO;
        }

        // Buscar el bracket que contiene el ingresoUvt
        RetentionBracket match = brackets.stream()
                .filter(b -> incomeInUvt.compareTo(b.getUvtMin()) >= 0
                        && (b.getUvtMax() == null || incomeInUvt.compareTo(b.getUvtMax()) < 0))
                .findFirst()
                .orElse(brackets.get(brackets.size() - 1));

        // retencion_uvt = (ingresoUvt - uvtOffset) * marginalRate + fixedUvtAmount
        BigDecimal offsetDiff = incomeInUvt.subtract(match.getUvtOffset());
        if (offsetDiff.compareTo(BigDecimal.ZERO) < 0) offsetDiff = BigDecimal.ZERO;
        BigDecimal retentionUvt = offsetDiff.multiply(match.getMarginalRate())
                .add(match.getFixedUvtAmount());
        if (retentionUvt.compareTo(BigDecimal.ZERO) < 0) retentionUvt = BigDecimal.ZERO;

        return retentionUvt.multiply(uvt).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal currentUvt() {
        return parameterRepository.findByNameAndDeletedAtIsNull(PARAM_UVT)
                .map(p -> {
                    try { return new BigDecimal(p.getValue()); }
                    catch (NumberFormatException ex) { return DEFAULT_UVT; }
                })
                .orElse(DEFAULT_UVT);
    }
}

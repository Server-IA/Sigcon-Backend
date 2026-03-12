package com.sigcon.backend.assets.assets_depreciation.domain.services;

import com.sigcon.backend.assets.assets.application.AssetDepreciationResultDTO;
import com.sigcon.backend.assets.assets.application.AssetSkippedDTO;
import com.sigcon.backend.assets.assets.application.AssetSkippedDTO.SkipReason;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.assets_depreciation.domain.model.enums.DepreciationMethod;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.assets.assets_depreciation.application.DepreciationCalculationResponseDTO;
import com.sigcon.backend.assets.assets_depreciation.domain.model.AssetDepreciation;
import com.sigcon.backend.assets.assets_depreciation.domain.repository.AssetDepreciationRepository;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.DepretationRule;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.repository.DepretationRuleRepository;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.utils.DepreciationCalculator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ACT-RF-02 — Cálculo Automático de Depreciación.
 * <p>
 * Responsabilidades:
 * <ul>
 * <li>Validar el período contable.</li>
 * <li>Obtener activos elegibles.</li>
 * <li>Aplicar reglas de negocio y tipificar exclusiones.</li>
 * <li>Delegar el cálculo matemático a {@link DepreciationCalculator}.</li>
 * <li>Persistir resultados en batch con {@code saveAll}.</li>
 * </ul>
 * Este servicio NO contiene fórmulas matemáticas.
 */
@Service
@RequiredArgsConstructor
public class DepreciationCalculationService {

    private static final List<AssetStatus> ELIGIBLE_STATUSES = List.of(AssetStatus.ACTIVE, AssetStatus.IN_REPAIR);

    private final AssetsRepository assetsRepository;
    private final DepretationRuleRepository depretationRuleRepository;
    private final AssetDepreciationRepository assetDepreciationRepository;

    /**
     * Ejecuta el cálculo de depreciación para todos los activos elegibles en el
     * período dado.
     *
     * @param period período contable en formato YYYY-MM (ej: "2026-03")
     * @return {@link DepreciationCalculationResponseDTO} con resultados y
     *         exclusiones
     */
    @Transactional
    public DepreciationCalculationResponseDTO calculate(String period) {
        validateAccountingPeriodIsOpen(period);

        List<Assets> candidates = assetsRepository.findEligibleForDepreciation(ELIGIBLE_STATUSES);

        List<AssetDepreciationResultDTO> results = new ArrayList<>();
        List<AssetSkippedDTO> skipped = new ArrayList<>();
        List<Assets> toSave = new ArrayList<>();
        List<AssetDepreciation> historyToSave = new ArrayList<>();
        BigDecimal totalDepreciation = BigDecimal.ZERO;
        LocalDate calculationDate = LocalDate.now();

        for (Assets asset : candidates) {

            // 1. Verificar estado (por si la query devuelve algo inconsistente)
            if (asset.getStatus() == AssetStatus.DECOMMISSIONED
                    || asset.getStatus() == AssetStatus.TRANSFERRED) {
                skipped.add(buildSkipped(asset, SkipReason.ASSET_INACTIVE));
                continue;
            }

            // 2. Validar vida útil
            if (asset.getUsefulLifeMonths() == null || asset.getUsefulLifeMonths() <= 0) {
                skipped.add(buildSkipped(asset, SkipReason.INVALID_USEFUL_LIFE));
                continue;
            }

            // 3. Validar método de depreciación
            if (asset.getDepreciationMethod() == null
                    || asset.getDepreciationMethod() == DepreciationMethod.OTHER) {
                skipped.add(buildSkipped(asset, SkipReason.NO_DEPRECIATION_METHOD));
                continue;
            }

            // 4. UNITS_OF_PRODUCTION no aplica para cálculo por período contable
            if (asset.getDepreciationMethod() == DepreciationMethod.UNITS_OF_PRODUCTION) {
                skipped.add(buildSkipped(asset, SkipReason.OTHER_METHOD));
                continue;
            }

            // 5. Buscar regla de depreciación activa para el método del activo
            DepretationType requiredType = mapMethodToType(asset.getDepreciationMethod());
            Optional<DepretationRule> ruleOpt = findActiveRule(requiredType);

            if (ruleOpt.isEmpty()) {
                skipped.add(buildSkipped(asset, SkipReason.NO_ACTIVE_RULE));
                continue;
            }

            DepretationRule rule = ruleOpt.get();

            // 6. Validar cuenta contable de la regla
            AccountingAccount depreciationAccount = rule.getAccountingAccount();
            if (depreciationAccount == null
                    || depreciationAccount.getStatus() != AccountStatus.ACTIVE) {
                skipped.add(buildSkipped(asset, SkipReason.NO_ACTIVE_RULE));
                continue;
            }

            // 7. Calcular valor actual en libros (si no tiene uno previo, usar
            // acquisitionValue)
            BigDecimal bookValue = asset.getCurrentBookValue() != null
                    ? asset.getCurrentBookValue()
                    : asset.getAcquisitionValue();

            // 8. Delegar cálculo al DepreciationCalculator (sin fórmulas aquí)
            BigDecimal depreciationAmount = computeDepreciation(
                    asset.getDepreciationMethod(),
                    asset.getAcquisitionValue(),
                    bookValue,
                    rule.getResidualValue(),
                    rule.getDepretationRate(),
                    asset.getUsefulLifeMonths());

            // 9. No depreciar por debajo del valor residual
            BigDecimal newBookValue = bookValue.subtract(depreciationAmount);
            BigDecimal safeResidual = rule.getResidualValue() != null ? rule.getResidualValue() : BigDecimal.ZERO;
            if (newBookValue.compareTo(safeResidual) < 0) {
                newBookValue = safeResidual;
                depreciationAmount = bookValue.subtract(safeResidual);
            }

            // 10. Acumular resultado
            totalDepreciation = totalDepreciation.add(depreciationAmount);

            results.add(AssetDepreciationResultDTO.builder()
                    .assetId(asset.getId())
                    .assetCode(asset.getAssetCode())
                    .assetName(asset.getAssetName())
                    .depreciationMethod(asset.getDepreciationMethod())
                    .depreciationAmount(depreciationAmount)
                    .previousBookValue(bookValue)
                    .currentBookValue(newBookValue)
                    .supplierName(asset.getSupplier() != null ? asset.getSupplier().getBusinessName() : null)
                    .accountingCode(asset.getChartOfAccount() != null ? asset.getChartOfAccount().getCode() : null)
                    .accountingName(asset.getChartOfAccount() != null ? asset.getChartOfAccount().getName() : null)
                    .depreciationAccountId(depreciationAccount.getId())
                    .depreciationAccountName(depreciationAccount.getCustomName())
                    .calculationDate(calculationDate)
                    .build());

            // 11. Actualizar activo en memoria
            asset.setCurrentBookValue(newBookValue);
            asset.setLastDepreciationDate(calculationDate);
            toSave.add(asset);

            // 12. Registrar en el histórico de depreciaciones
            historyToSave.add(AssetDepreciation.builder()
                    .asset(asset)
                    .depreciationPeriod(period)
                    .previousBookValue(bookValue)
                    .currentBookValue(newBookValue)
                    .depreciationAmount(depreciationAmount)
                    .depreciationMethod(asset.getDepreciationMethod())
                    .calculationDate(calculationDate)
                    .build());
        }

        // 13. Persistir activos actualizados en batch
        if (!toSave.isEmpty()) {
            assetsRepository.saveAll(toSave);
        }

        // 14. Persistir histórico de depreciaciones en batch
        if (!historyToSave.isEmpty()) {
            assetDepreciationRepository.saveAll(historyToSave);
        }

        return DepreciationCalculationResponseDTO.builder()
                .period(period)
                .processedCount(results.size())
                .skippedCount(skipped.size())
                .totalDepreciation(totalDepreciation)
                .results(results)
                .skipped(skipped)
                .message("Depreciación calculada exitosamente")
                .build();
    }

    // -------------------------------------------------------------------------
    // Métodos privados de soporte
    // -------------------------------------------------------------------------

    /**
     * Valida que el período contable esté abierto.
     * TODO: integrar con el módulo de períodos contables cuando esté disponible.
     */
    private void validateAccountingPeriodIsOpen(String period) {
        boolean accountingPeriodOpen = true;
        if (!accountingPeriodOpen) {
            throw new IllegalStateException("Operación no permitida. Periodo contable cerrado");
        }
    }

    /**
     * Mapea el DepreciationMethod del activo al DepretationType de las reglas.
     */
    private DepretationType mapMethodToType(DepreciationMethod method) {
        return switch (method) {
            case STRAIGHT_LINE -> DepretationType.LINEAR;
            case DECLINING_BALANCE -> DepretationType.DECREASING;
            case UNITS_OF_PRODUCTION -> DepretationType.PRODUCTION_UNITS;
            case OTHER -> throw new IllegalArgumentException("Método no reconocido o no permitido");
        };
    }

    /**
     * Busca la regla de depreciación activa más reciente para un tipo dado.
     * Retorna la primera regla activa ordenada descendentemente por effectiveDate.
     */
    private Optional<DepretationRule> findActiveRule(DepretationType type) {
        return depretationRuleRepository
                .findAll()
                .stream()
                .filter(r -> r.getDepretationType() == type
                        && r.getStatus() == DepretationStatus.ACTIVE
                        && r.getDeletedAt() == null)
                .max((a, b) -> a.getEffectiveDate().compareTo(b.getEffectiveDate()));
    }

    /**
     * Delega el cálculo al utilitario DepreciationCalculator según el método.
     */
    private BigDecimal computeDepreciation(
            DepreciationMethod method,
            BigDecimal acquisitionValue,
            BigDecimal currentBookValue,
            BigDecimal residualValue,
            BigDecimal annualRate,
            int usefulLifeMonths) {

        return switch (method) {
            case STRAIGHT_LINE ->
                DepreciationCalculator.calculateStraightLine(acquisitionValue, residualValue, usefulLifeMonths);
            case DECLINING_BALANCE ->
                DepreciationCalculator.calculateDecliningBalance(currentBookValue, annualRate);
            default ->
                throw new IllegalArgumentException("Método no reconocido o no permitido");
        };
    }

    /**
     * Construye un DTO de activo excluido.
     */
    private AssetSkippedDTO buildSkipped(Assets asset, SkipReason reason) {
        return AssetSkippedDTO.builder()
                .assetId(asset.getId())
                .assetCode(asset.getAssetCode())
                .assetName(asset.getAssetName())
                .reason(reason)
                .build();
    }
}

package com.sigcon.backend.assets.assets_depreciation.domain.services;

import com.sigcon.backend.assets.assets.application.AssetDepreciationResultDTO;
import com.sigcon.backend.assets.assets.application.AssetSkippedDTO;
import com.sigcon.backend.assets.assets.application.AssetSkippedDTO.SkipReason;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
// import com.sigcon.backend.assets.assets_depreciation.domain.model.enums.DepreciationMethod;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.assets.assets_depreciation.application.DepreciationCalculationResponseDTO;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.DepretationRule;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;
import com.sigcon.backend.assets.assets_depreciation.domain.model.AssetDepreciation;
import com.sigcon.backend.assets.assets_depreciation.domain.repository.AssetDepreciationRepository;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.repository.DepretationRuleRepository;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.utils.DepreciationCalculator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class DepreciationCalculationService {

    private static final List<AssetStatus> ELIGIBLE_STATUSES = List.of(AssetStatus.ACTIVE, AssetStatus.IN_REPAIR);

    private final AssetsRepository assetsRepository;
    private final DepretationRuleRepository depretationRuleRepository;
    private final AssetDepreciationRepository assetDepreciationRepository;
    private final JournalEntryService journalEntryService;
    private final AccountingPeriodService accountingPeriodService;
    private final AuditPublisher auditPublisher;

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

            // // 3. Validar método de depreciación
            // if (asset.getDepretationRule() == null
            // || asset.getDepretationRule().getDepretationType() == DepretationType.OTHER)
            // {
            // skipped.add(buildSkipped(asset, SkipReason.NO_DEPRECIATION_METHOD));
            // continue;
            // }

            // 4. UNITS_OF_PRODUCTION no aplica para cálculo por período contable
            if (asset.getDepretationRule().getDepretationType() == DepretationType.PRODUCTION_UNITS) {
                skipped.add(buildSkipped(asset, SkipReason.OTHER_METHOD));
                continue;
            }

            // 5. Buscar regla de depreciación activa para el método del activo
            DepretationType requiredType = asset.getDepretationRule().getDepretationType();

            Optional<DepretationRule> depretationRule = depretationRuleRepository
                    .findById(asset.getDepretationRule().getId());

            if (depretationRule.isEmpty()) {
                skipped.add(buildSkipped(asset, SkipReason.NO_ACTIVE_RULE));
                continue;
            }

            DepretationRule rule = depretationRule.get();



            // 7. Validar cuenta contable de la regla
            AccountingAccount depreciationAccount = rule.getAccountingAccount();
            if (depreciationAccount == null
                    || depreciationAccount.getStatus() != AccountStatus.ACTIVE) {
                skipped.add(buildSkipped(asset, SkipReason.NO_ACTIVE_RULE));
                continue;
            }

            // 8. Calcular valor actual en libros (si no tiene uno previo, usar
            // acquisitionValue)
            BigDecimal bookValue = asset.getCurrentBookValue() != null
                    ? asset.getCurrentBookValue()
                    : asset.getAcquisitionValue();

            // 9. Delegar cálculo al DepreciationCalculator (sin fórmulas aquí)
            BigDecimal depreciationAmount = computeDepreciation(
                    rule.getDepretationType(),
                    asset.getAcquisitionValue(),
                    bookValue,
                    rule.getResidualValue(),
                    rule.getDepretationRate(),
                    asset.getUsefulLifeMonths());

            // 10. No depreciar por debajo del valor residual
            BigDecimal newBookValue = bookValue.subtract(depreciationAmount);
            BigDecimal safeResidual = rule.getResidualValue() != null ? rule.getResidualValue() : BigDecimal.ZERO;
            if (newBookValue.compareTo(safeResidual) < 0) {
                newBookValue = safeResidual;
                depreciationAmount = bookValue.subtract(safeResidual);
            }

            // 11. Acumular resultado
            totalDepreciation = totalDepreciation.add(depreciationAmount);

            results.add(AssetDepreciationResultDTO.builder()
                    .assetId(asset.getId())
                    .assetCode(asset.getAssetCode())
                    .assetName(asset.getAssetName())
                    .depreciationMethod(rule.getDepretationType())
                    .depreciationAmount(depreciationAmount)
                    .previousBookValue(bookValue)
                    .currentBookValue(newBookValue)
                    .supplier(asset.getSupplier() != null ? ThirdPartyDTO.builder()
                            .id(asset.getSupplier().getId())
                            .businessName(asset.getSupplier().getBusinessName())
                            .build() : null)
                    // .accountingCode(asset.getChartOfAccount() != null ?
                    // asset.getChartOfAccount().getCode() : null)
                    // .accountingName(asset.getChartOfAccount() != null ?
                    // asset.getChartOfAccount().getName() : null)
                    // .accountingAccount(AccountingAccountDTO.builder()
                    // .id(depreciationAccount.getId())
                    // .code(depreciationAccount.getCode())
                    // .name(depreciationAccount.getName())
                    // .build())
                    // .depreciationAccountName(depreciationAccount.getCustomName())
                    .calculationDate(calculationDate)
                    .build());

            // 12. Actualizar activo en memoria
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
                    .depretationType(rule.getDepretationType())
                    .calculationDate(calculationDate)
                    .build());
        }

        // 13. Persistir activos actualizados en batch
        if (!toSave.isEmpty()) {
            assetsRepository.saveAll(toSave);
        }
        if (!historyToSave.isEmpty()) {
            assetDepreciationRepository.saveAll(historyToSave);
        }

        // 14. Generar asiento contable consolidado para las depreciaciones del periodo
        if (!historyToSave.isEmpty()) {
            createDepreciationJournalEntry(period, historyToSave, candidates);
        }

        // Audit log: registrar la corrida de depreciacion (HU-ACT-02 - operacion masiva
        // que toca N activos + genera JE consolidado, debe quedar trazada en auditoria).
        if (!results.isEmpty()) {
            auditPublisher.publish(
                    AuditAction.UPDATE, AuditModule.ACT, AuditSeverity.MEDIUM,
                    "AssetDepreciation", null,
                    "Depreciacion calculada para periodo " + period
                            + " | activos procesados: " + results.size()
                            + " | omitidos: " + skipped.size()
                            + " | total depreciado: $" + totalDepreciation,
                    null, null, null);
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
     * ACT-02: Valida que el periodo contable (formato "YYYY-MM") este abierto
     * antes de permitir el calculo de depreciacion.
     * Delega en {@link AccountingPeriodService#validatePeriodOpen}.
     *
     * @param period periodo en formato "YYYY-MM"
     * @throws IllegalArgumentException si el formato es invalido
     * @throws IllegalStateException    si el periodo esta cerrado/bloqueado
     */
    private void validateAccountingPeriodIsOpen(String period) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException(
                    "Periodo invalido: '" + period + "'. Formato esperado: YYYY-MM");
        }
        String[] parts = period.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        // Usa una fecha del primer dia del mes como referencia para el periodo.
        java.time.LocalDate referenceDate = java.time.LocalDate.of(year, month, 1);
        accountingPeriodService.validatePeriodOpen(referenceDate);
    }

    /**
     * Mapea el DepretationType de la regla al enum DepreciationMethod requerido por
     * el servicio.
     */
    // private DepretationType mapMethodToType(DepreciationMethod method) {
    // return switch (method) {
    // case STRAIGHT_LINE -> DepretationType.LINEAR;
    // case DECLINING_BALANCE -> DepretationType.DECREASING;
    // case UNITS_OF_PRODUCTION -> DepretationType.PRODUCTION_UNITS;
    // case OTHER -> throw new IllegalArgumentException("Método no reconocido o no
    // permitido");
    // };
    // }

    /**
     * Delega el cálculo al utilitario DepreciationCalculator según el método.
     */
    private BigDecimal computeDepreciation(
            DepretationType method,
            BigDecimal acquisitionValue,
            BigDecimal currentBookValue,
            BigDecimal residualValue,
            BigDecimal annualRate,
            int usefulLifeMonths) {

        return switch (method) {
            case LINEAR ->
                DepreciationCalculator.calculateStraightLine(acquisitionValue, residualValue, usefulLifeMonths);
            case DECREASING ->
                DepreciationCalculator.calculateDecliningBalance(currentBookValue, annualRate);
            case ACCELERATED ->
                // Depreciación acelerada: el doble de la línea recta
                DepreciationCalculator.calculateStraightLine(acquisitionValue, residualValue, usefulLifeMonths)
                    .multiply(BigDecimal.valueOf(2));
            case PRODUCTION_UNITS ->
                // Unidades de producción: se calcula como línea recta por defecto
                // TODO: Integrar con datos de producción real cuando estén disponibles
                DepreciationCalculator.calculateStraightLine(acquisitionValue, residualValue, usefulLifeMonths);
            case MINIMUN_USEFUL_LIFE ->
                // Vida útil mínima: se usa línea recta con la vida útil configurada
                DepreciationCalculator.calculateStraightLine(acquisitionValue, residualValue, usefulLifeMonths);
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

    /**
     * Genera un asiento contable consolidado para todas las depreciaciones calculadas en el periodo.
     * <p>
     * Para cada activo depreciado se crean dos lineas:
     * <ul>
     *   <li><b>Debito:</b> cuenta de gasto por depreciacion (regla de depreciacion).</li>
     *   <li><b>Credito:</b> cuenta contable del activo (depreciacion acumulada).</li>
     * </ul>
     * Si la creacion del asiento falla, se registra una advertencia en el log
     * pero no se interrumpe el proceso de depreciacion.
     *
     * @param period        periodo contable en formato YYYY-MM
     * @param depreciations registros de depreciacion recien guardados
     * @param candidates    lista de activos candidatos (para acceder a relaciones)
     */
    private void createDepreciationJournalEntry(String period,
                                                 List<AssetDepreciation> depreciations,
                                                 List<Assets> candidates) {
        try {
            List<CreateJournalEntryLineRequest> journalLines = new ArrayList<>();

            for (AssetDepreciation depreciation : depreciations) {
                Assets asset = depreciation.getAsset();
                BigDecimal amount = depreciation.getDepreciationAmount();

                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // Linea debito: cuenta de gasto por depreciacion (desde la regla)
                Long depreciationAccountId = asset.getDepretationRule() != null
                        && asset.getDepretationRule().getAccountingAccount() != null
                        ? asset.getDepretationRule().getAccountingAccount().getId()
                        : null;

                // Linea credito: cuenta contable del activo (depreciacion acumulada)
                Long assetAccountId = asset.getAccountingAccount() != null
                        ? asset.getAccountingAccount().getId()
                        : null;

                if (depreciationAccountId == null || assetAccountId == null) {
                    log.warn("Activo {} sin cuentas contables configuradas, se omite del asiento.",
                            asset.getAssetCode());
                    continue;
                }

                // Debito: gasto depreciacion
                journalLines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(depreciationAccountId)
                        .debitAmount(amount)
                        .creditAmount(BigDecimal.ZERO)
                        .description("Depreciacion " + asset.getAssetCode() + " - " + asset.getAssetName())
                        .build());

                // Credito: depreciacion acumulada del activo
                journalLines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(assetAccountId)
                        .debitAmount(BigDecimal.ZERO)
                        .creditAmount(amount)
                        .description("Depreciacion acumulada " + asset.getAssetCode() + " - " + asset.getAssetName())
                        .build());
            }

            if (journalLines.isEmpty()) {
                log.info("No se generaron lineas de asiento contable para el periodo {}", period);
                return;
            }

            CreateJournalEntryRequest journalRequest = CreateJournalEntryRequest.builder()
                    .entryDate(LocalDate.now())
                    .description("Depreciacion de activos periodo " + period)
                    .sourceModule(JournalSourceModule.ACT)
                    .lines(journalLines)
                    .build();

            journalEntryService.createEntry(journalRequest, "sistema");

            log.info("Asiento contable de depreciacion creado exitosamente para el periodo {} con {} lineas.",
                    period, journalLines.size());

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento de depreciacion para el periodo {}: {}",
                    period, e.getMessage());
            throw new IllegalStateException(
                    "No se pudo calcular la depreciacion: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento de depreciacion para el periodo {}", period, e);
            throw e;
        }
    }
}

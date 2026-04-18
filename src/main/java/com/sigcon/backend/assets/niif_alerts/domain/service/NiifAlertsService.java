package com.sigcon.backend.assets.niif_alerts.domain.service;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.DepretationRule;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.repository.DepretationRuleRepository;
import com.sigcon.backend.assets.niif_alerts.application.*;
import com.sigcon.backend.assets.niif_alerts.domain.model.*;
import com.sigcon.backend.assets.niif_alerts.domain.model.enums.*;
import com.sigcon.backend.assets.niif_alerts.domain.repository.*;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Servicio para verificacion de cumplimiento NIIF, revisiones anuales
 * y correcciones contables de activos fijos.
 * Implementa HU-ACT-05 (verificacion), HU-ACT-06 (correcciones),
 * HU-ACT-11 (revaluacion NIC 16), HU-ACT-12 (revision anual),
 * HU-ACT-13 (verificacion mejorada) y HU-ACT-14 (cambio estimacion NIC 8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NiifAlertsService {

    private final AssetsRepository assetsRepository;
    private final NiifVerificationRepository verificationRepository;
    private final NiifAlertRepository alertRepository;
    private final NiifCorrectionRepository correctionRepository;
    private final AssetAnnualReviewRepository annualReviewRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final JournalEntryService journalEntryService;
    private final DepretationRuleRepository depretationRuleRepository;

    // ───────────────────────────────────────────────────────────────
    // HU-ACT-05 / HU-ACT-13: Verificacion NIIF mejorada
    // ───────────────────────────────────────────────────────────────

    /**
     * Verifica el cumplimiento NIIF de una lista de activos.
     * Ejecuta 6 checks: vida util valida, valor libros vs adquisicion,
     * depreciacion desactualizada, valor residual negativo,
     * depreciacion acumulada excedida, y activo sin verificacion en 12+ meses.
     *
     * @param request lista de IDs de activos a verificar
     * @return resultados de verificacion por activo
     */
    @Transactional
    public List<NiifVerificationResultDTO> verifyAssets(VerifyNiifRequest request) {

        List<Assets> assets = assetsRepository.findAllById(request.getAssetIds());
        List<NiifVerificationResultDTO> results = new ArrayList<>();

        for (Assets asset : assets) {

            List<String> alerts = new ArrayList<>();
            NiifResult result = NiifResult.COMPLIANT;

            // Check 1: Vida util valida
            if (asset.getUsefulLifeMonths() == null || asset.getUsefulLifeMonths() <= 0) {
                alerts.add("El activo no tiene vida útil válida");
                result = NiifResult.NON_COMPLIANT;
            }

            // Check 2: Valor en libros no supera valor de adquisicion
            if (asset.getCurrentBookValue() != null
                    && asset.getCurrentBookValue().compareTo(asset.getAcquisitionValue()) > 0) {
                alerts.add("El valor en libros supera el valor de adquisición");
                result = NiifResult.WARNING;
            }

            // Check 3: Depreciacion actualizada (no mas de 12 meses sin depreciar)
            if (asset.getLastDepreciationDate() != null
                    && asset.getLastDepreciationDate().isBefore(LocalDate.now().minusMonths(12))) {
                alerts.add("El activo no ha sido depreciado en más de 12 meses");
                if (result != NiifResult.NON_COMPLIANT) {
                    result = NiifResult.WARNING;
                }
            }

            // Check 4: Valor residual >= 0 (si existe valor en libros)
            if (asset.getCurrentBookValue() != null && asset.getDepretationRule() != null) {
                BigDecimal residualValue = asset.getDepretationRule().getResidualValue();
                if (residualValue != null && residualValue.compareTo(BigDecimal.ZERO) < 0) {
                    alerts.add("El valor residual es negativo, lo cual viola NIC 16");
                    result = NiifResult.NON_COMPLIANT;
                }
            }

            // Check 5: Depreciacion acumulada no excede (acquisitionValue - residualValue)
            if (asset.getCurrentBookValue() != null && asset.getAcquisitionValue() != null
                    && asset.getDepretationRule() != null) {
                BigDecimal residual = asset.getDepretationRule().getResidualValue() != null
                        ? asset.getDepretationRule().getResidualValue()
                        : BigDecimal.ZERO;
                BigDecimal depreciationAccumulated = asset.getAcquisitionValue()
                        .subtract(asset.getCurrentBookValue());
                BigDecimal maxDepreciation = asset.getAcquisitionValue().subtract(residual);

                if (depreciationAccumulated.compareTo(maxDepreciation) > 0) {
                    alerts.add("La depreciación acumulada excede el límite permitido "
                            + "(valor adquisición - valor residual)");
                    if (result != NiifResult.NON_COMPLIANT) {
                        result = NiifResult.WARNING;
                    }
                }
            }

            // Check 6: Activo activo por mas de 12 meses sin verificacion NIIF
            if (asset.getCreatedAt() != null && asset.getStatus() == AssetStatus.ACTIVE) {
                long monthsActive = ChronoUnit.MONTHS.between(
                        asset.getCreatedAt().toLocalDate(), LocalDate.now());
                if (monthsActive > 12) {
                    // Verificar si tiene alguna verificacion reciente
                    boolean hasRecentVerification = asset.getLastDepreciationDate() != null
                            && !asset.getLastDepreciationDate().isBefore(LocalDate.now().minusMonths(12));
                    if (!hasRecentVerification) {
                        alerts.add("El activo lleva más de 12 meses activo sin verificación NIIF reciente");
                        if (result != NiifResult.NON_COMPLIANT) {
                            result = NiifResult.WARNING;
                        }
                    }
                }
            }

            // Persistir verificacion y alertas
            NiifVerification verification = verificationRepository.save(
                    NiifVerification.builder()
                            .asset(asset)
                            .result(result)
                            .summary("Verificación automática NIIF")
                            .build()
            );

            for (String msg : alerts) {
                NiifSeverity severity = msg.contains("negativo") || msg.contains("vida útil válida")
                        ? NiifSeverity.CRITICAL
                        : NiifSeverity.WARNING;
                alertRepository.save(
                        NiifAlert.builder()
                                .verification(verification)
                                .severity(severity)
                                .message(msg)
                                .build()
                );
            }

            results.add(
                    NiifVerificationResultDTO.builder()
                            .assetId(asset.getId())
                            .assetName(asset.getAssetName())
                            .result(result.name())
                            .alerts(alerts)
                            .build()
            );
        }

        return results;
    }

    // ───────────────────────────────────────────────────────────────
    // HU-ACT-12: Revision anual de vida util y valor residual
    // ───────────────────────────────────────────────────────────────

    /**
     * Lista activos activos elegibles para revision anual.
     * Calcula la depreciacion mensual actual de cada activo para facilitar
     * la decision de revision.
     *
     * @param fiscalYear anio fiscal para el cual se realiza la revision
     * @return lista de activos con sus datos de depreciacion actual
     */
    public List<Map<String, Object>> listAssetsForReview(Integer fiscalYear) {
        List<Assets> activeAssets = assetsRepository.findEligibleForDepreciation(
                List.of(AssetStatus.ACTIVE, AssetStatus.IN_REPAIR));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Assets asset : activeAssets) {
            Map<String, Object> assetData = new LinkedHashMap<>();
            assetData.put("id", asset.getId());
            assetData.put("code", asset.getAssetCode());
            assetData.put("name", asset.getAssetName());
            assetData.put("usefulLifeMonths", asset.getUsefulLifeMonths());

            BigDecimal residualValue = BigDecimal.ZERO;
            if (asset.getDepretationRule() != null && asset.getDepretationRule().getResidualValue() != null) {
                residualValue = asset.getDepretationRule().getResidualValue();
            }
            assetData.put("residualValue", residualValue);

            BigDecimal currentBookValue = asset.getCurrentBookValue() != null
                    ? asset.getCurrentBookValue()
                    : asset.getAcquisitionValue();
            assetData.put("currentBookValue", currentBookValue);

            // Calcular depreciacion mensual = (acquisitionValue - residualValue) / usefulLifeMonths
            BigDecimal depreciationMonthly = BigDecimal.ZERO;
            if (asset.getUsefulLifeMonths() != null && asset.getUsefulLifeMonths() > 0) {
                BigDecimal depreciableAmount = asset.getAcquisitionValue().subtract(residualValue);
                depreciationMonthly = depreciableAmount.divide(
                        BigDecimal.valueOf(asset.getUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
            }
            assetData.put("depreciationMonthly", depreciationMonthly);

            // Indicar si ya tiene revision para este anio fiscal
            boolean reviewed = annualReviewRepository
                    .findByAssetIdAndFiscalYearAndDeletedAtIsNull(asset.getId(), fiscalYear)
                    .isPresent();
            assetData.put("reviewedThisYear", reviewed);

            result.add(assetData);
        }

        return result;
    }

    /**
     * Registra una revision anual de activo segun NIC 16.
     * Si newUsefulLife y newResidualValue son nulos, se registra como CONFIRMED.
     * Si alguno cambia, se recalcula la depreciacion prospectiva y se actualiza el activo.
     * Operacion idempotente: si ya existe revision para el activo+anio, retorna la existente.
     *
     * @param request datos de la revision
     * @return mensaje de exito con datos de la revision
     */
    @Transactional
    public Map<String, Object> createAnnualReview(CreateAnnualReviewRequest request) {
        // 1. Buscar y validar activo
        Assets asset = assetsRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Activo no encontrado con ID: " + request.getAssetId()));

        if (asset.getStatus() != AssetStatus.ACTIVE) {
            throw new IllegalArgumentException("Solo se pueden revisar activos en estado ACTIVE.");
        }

        // 2. Verificar idempotencia
        Optional<AssetAnnualReview> existing = annualReviewRepository
                .findByAssetIdAndFiscalYearAndDeletedAtIsNull(request.getAssetId(), request.getFiscalYear());

        if (existing.isPresent()) {
            AssetAnnualReview existingReview = existing.get();
            return Map.of(
                    "message", "Ya existe una revisión para este activo en el año fiscal " + request.getFiscalYear(),
                    "review", toAnnualReviewDTO(existingReview)
            );
        }

        // 3. Obtener valores actuales
        Integer previousUsefulLife = asset.getUsefulLifeMonths();
        BigDecimal previousResidualValue = asset.getDepretationRule() != null
                && asset.getDepretationRule().getResidualValue() != null
                ? asset.getDepretationRule().getResidualValue()
                : BigDecimal.ZERO;

        BigDecimal currentBookValue = asset.getCurrentBookValue() != null
                ? asset.getCurrentBookValue()
                : asset.getAcquisitionValue();

        // Calcular depreciacion mensual actual
        BigDecimal previousDepreciationMonthly = BigDecimal.ZERO;
        int remainingLife = previousUsefulLife != null && previousUsefulLife > 0 ? previousUsefulLife : 1;
        if (currentBookValue.compareTo(BigDecimal.ZERO) > 0) {
            previousDepreciationMonthly = currentBookValue.divide(
                    BigDecimal.valueOf(remainingLife), 2, RoundingMode.HALF_UP);
        }

        // 4. Determinar tipo de revision y calcular nuevos valores
        String reviewType;
        BigDecimal newDepreciationMonthly = previousDepreciationMonthly;
        Integer newUsefulLife = request.getNewUsefulLife();
        BigDecimal newResidualValue = request.getNewResidualValue();

        boolean usefulLifeChanged = newUsefulLife != null && !newUsefulLife.equals(previousUsefulLife);
        boolean residualChanged = newResidualValue != null
                && newResidualValue.compareTo(previousResidualValue) != 0;

        if (usefulLifeChanged && residualChanged) {
            reviewType = "USEFUL_LIFE_CHANGE";
            // Recalcular: (currentBookValue - newResidualValue) / newUsefulLife
            BigDecimal depreciable = currentBookValue.subtract(newResidualValue);
            newDepreciationMonthly = newUsefulLife > 0
                    ? depreciable.divide(BigDecimal.valueOf(newUsefulLife), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        } else if (usefulLifeChanged) {
            reviewType = "USEFUL_LIFE_CHANGE";
            // Recalcular: (currentBookValue - previousResidualValue) / newUsefulLife
            BigDecimal depreciable = currentBookValue.subtract(previousResidualValue);
            newDepreciationMonthly = newUsefulLife > 0
                    ? depreciable.divide(BigDecimal.valueOf(newUsefulLife), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        } else if (residualChanged) {
            reviewType = "RESIDUAL_VALUE_CHANGE";
            // Recalcular: (currentBookValue - newResidualValue) / remainingLife
            BigDecimal depreciable = currentBookValue.subtract(newResidualValue);
            newDepreciationMonthly = remainingLife > 0
                    ? depreciable.divide(BigDecimal.valueOf(remainingLife), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        } else {
            reviewType = "CONFIRMED";
        }

        // 5. Guardar revision
        AssetAnnualReview review = AssetAnnualReview.builder()
                .asset(asset)
                .reviewDate(LocalDate.now())
                .fiscalYear(request.getFiscalYear())
                .previousUsefulLife(previousUsefulLife)
                .newUsefulLife(usefulLifeChanged ? newUsefulLife : previousUsefulLife)
                .previousResidualValue(previousResidualValue)
                .newResidualValue(residualChanged ? newResidualValue : previousResidualValue)
                .previousDepreciationMonthly(previousDepreciationMonthly)
                .newDepreciationMonthly(newDepreciationMonthly)
                .reviewType(reviewType)
                .justification(request.getJustification())
                .build();

        AssetAnnualReview saved = annualReviewRepository.save(review);

        // 6. Si cambio la vida util, actualizar el activo
        if (usefulLifeChanged) {
            asset.setUsefulLifeMonths(newUsefulLife);
            assetsRepository.save(asset);
        }

        return Map.of(
                "message", "Revisión anual registrada correctamente",
                "review", toAnnualReviewDTO(saved)
        );
    }

    // ───────────────────────────────────────────────────────────────
    // HU-ACT-06 / HU-ACT-11 / HU-ACT-14: Correcciones NIIF
    // ───────────────────────────────────────────────────────────────

    /**
     * Aplica correcciones NIIF con validaciones de rango, periodo y generacion
     * de asiento contable automatico.
     * <ul>
     *   <li>REVALUATION (NIC 16 S31): Actualiza valor en libros y genera asiento al ORI.</li>
     *   <li>USEFUL_LIFE_ADJUSTMENT (NIC 8): Recalcula depreciacion prospectiva.</li>
     *   <li>IMPAIRMENT_REVERSAL (NIC 36): Revierte deterioro sin exceder valor original.</li>
     * </ul>
     *
     * @param request datos de la correccion a aplicar
     * @return mensaje de confirmacion
     */
    @Transactional
    public String applyCorrection(ApplyNiifCorrectionRequest request) {

        Assets asset = assetsRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Activo no encontrado"));

        // Validar que el periodo contable no este cerrado
        accountingPeriodService.validatePeriodOpen(LocalDate.now());

        // --- REVALUATION (NIC 16 S31) ---
        if (request.getCorrectionType() == NiifCorrectionType.REVALUATION) {
            // ACT-11 E3: Verificar que el activo no use modelo de costo puro
            // Un activo sin revaluaciones previas y con depreciacion lineal usa modelo de costo por defecto
            // Se permite revaluacion solo si el usuario explicitamente lo solicita (campo observations debe indicar cambio de politica)
            if (request.getObservations() == null || request.getObservations().isBlank()) {
                throw new IllegalArgumentException(
                        "Debe indicar en las observaciones el motivo de la revaluacion y confirmar el cambio de politica contable si aplica.");
            }
            if (request.getNewBookValue() != null) {
                BigDecimal maxValue = new BigDecimal("999999999999");
                if (request.getNewBookValue().compareTo(maxValue) > 0) {
                    throw new IllegalArgumentException(
                            "Valor fuera de rango permitido por NIIF. El valor máximo es $" + maxValue);
                }
                if (request.getNewBookValue().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("El valor de revaluación no puede ser negativo");
                }
            }

            BigDecimal oldBookValue = asset.getCurrentBookValue() != null
                    ? asset.getCurrentBookValue()
                    : asset.getAcquisitionValue();
            asset.setCurrentBookValue(request.getNewBookValue());
            assetsRepository.save(asset);

            // Generar asiento contable para la revaluacion
            BigDecimal difference = request.getNewBookValue().subtract(oldBookValue);
            if (difference.compareTo(BigDecimal.ZERO) != 0 && asset.getAccountingAccount() != null) {
                try {
                    BigDecimal debitAmount = difference.compareTo(BigDecimal.ZERO) > 0
                            ? difference : BigDecimal.ZERO;
                    BigDecimal creditAmount = difference.compareTo(BigDecimal.ZERO) > 0
                            ? BigDecimal.ZERO : difference.abs();

                    List<CreateJournalEntryLineRequest> lines = List.of(
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(asset.getAccountingAccount().getId())
                                    .debitAmount(debitAmount)
                                    .creditAmount(creditAmount)
                                    .description("Revaluación activo " + asset.getAssetCode())
                                    .build(),
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(asset.getAccountingAccount().getId())
                                    .debitAmount(creditAmount)
                                    .creditAmount(debitAmount)
                                    .description("Superávit revaluación - ORI")
                                    .build()
                    );

                    CreateJournalEntryRequest journalRequest = CreateJournalEntryRequest.builder()
                            .entryDate(LocalDate.now())
                            .description("Revaluación activo " + asset.getAssetCode()
                                    + " - Diferencia: $" + difference)
                            .sourceModule(JournalSourceModule.ACT)
                            .sourceId(asset.getId())
                            .lines(lines)
                            .build();

                    journalEntryService.createEntry(journalRequest, "sistema");
                    log.info("Asiento contable de revaluación creado para activo {}", asset.getAssetCode());
                } catch (Exception e) {
                    log.warn("No se pudo crear asiento contable para revaluación del activo {}: {}",
                            asset.getAssetCode(), e.getMessage());
                }
            }
        }

        // --- USEFUL_LIFE_ADJUSTMENT (NIC 8 - Cambio de estimacion) ---
        if (request.getCorrectionType() == NiifCorrectionType.USEFUL_LIFE_ADJUSTMENT) {
            if (request.getNewUsefulLifeMonths() != null && request.getNewUsefulLifeMonths() <= 0) {
                throw new IllegalArgumentException("La vida útil debe ser mayor a 0 meses");
            }
            asset.setUsefulLifeMonths(request.getNewUsefulLifeMonths());
            assetsRepository.save(asset);

            // Recalcular depreciacion prospectiva
            BigDecimal currentBookValue = asset.getCurrentBookValue() != null
                    ? asset.getCurrentBookValue()
                    : asset.getAcquisitionValue();
            BigDecimal newMonthlyDepr = BigDecimal.ZERO;
            if (request.getNewUsefulLifeMonths() != null && request.getNewUsefulLifeMonths() > 0) {
                newMonthlyDepr = currentBookValue.divide(
                        BigDecimal.valueOf(request.getNewUsefulLifeMonths()), 2, RoundingMode.HALF_UP);
            }

            // Incluir depreciacion recalculada en observaciones de la correccion
            String observations = request.getObservations() != null ? request.getObservations() : "";
            observations += " | Depreciación mensual recalculada: $" + newMonthlyDepr;
            request.setObservations(observations.trim());
        }

        // --- DEPRECIATION_METHOD_CHANGE (NIC 8 - Cambio estimacion) ---
        // Permite reasignar la regla de depreciacion del activo (metodo/tasa/vida util).
        // Tratado como cambio prospectivo: no toca saldos contables historicos,
        // solo afecta calculos futuros via DepreciationCalculationService.
        if (request.getCorrectionType() == NiifCorrectionType.DEPRECIATION_METHOD_CHANGE) {
            if (request.getNewDepretationRuleId() == null) {
                throw new IllegalArgumentException(
                        "newDepretationRuleId es obligatorio para cambio de metodo de depreciacion");
            }
            DepretationRule newRule = depretationRuleRepository.findById(request.getNewDepretationRuleId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Regla de depreciacion no encontrada: " + request.getNewDepretationRuleId()));
            if (newRule.getDeletedAt() != null || !"ACTIVE".equalsIgnoreCase(
                    newRule.getStatus() != null ? newRule.getStatus().name() : "")) {
                throw new IllegalArgumentException(
                        "La regla de depreciacion seleccionada no esta activa");
            }

            String oldMethod = asset.getDepretationRule() != null && asset.getDepretationRule().getDepretationType() != null
                    ? asset.getDepretationRule().getDepretationType().name()
                    : "N/A";
            String newMethod = newRule.getDepretationType() != null
                    ? newRule.getDepretationType().name() : "N/A";

            asset.setDepretationRule(newRule);
            assetsRepository.save(asset);

            String observations = request.getObservations() != null ? request.getObservations() : "";
            observations += " | Cambio metodo depreciacion: " + oldMethod + " -> " + newMethod
                    + " (aplicacion prospectiva - NIC 8)";
            request.setObservations(observations.trim());
        }

        // --- IMPAIRMENT_REVERSAL (NIC 36) ---
        if (request.getCorrectionType() == NiifCorrectionType.IMPAIRMENT_REVERSAL) {
            if (request.getNewBookValue() == null) {
                throw new IllegalArgumentException(
                        "El nuevo valor en libros es obligatorio para reversión de deterioro");
            }
            if (request.getNewBookValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "El valor de reversión de deterioro no puede ser negativo");
            }
            // Validar que no exceda el valor original de adquisicion
            if (request.getNewBookValue().compareTo(asset.getAcquisitionValue()) > 0) {
                throw new IllegalArgumentException(
                        "La reversión de deterioro no puede exceder el valor original de adquisición ($"
                                + asset.getAcquisitionValue() + ")");
            }
            asset.setCurrentBookValue(request.getNewBookValue());
            assetsRepository.save(asset);
        }

        // Guardar registro de correccion
        correctionRepository.save(
                NiifCorrection.builder()
                        .asset(asset)
                        .correctionType(request.getCorrectionType())
                        .newUsefulLifeMonths(request.getNewUsefulLifeMonths())
                        .newBookValue(request.getNewBookValue())
                        .observations(request.getObservations())
                        .build()
        );

        return "Corrección aplicada correctamente";
    }

    // ───────────────────────────────────────────────────────────────
    // Utilidades privadas
    // ───────────────────────────────────────────────────────────────

    /**
     * Convierte una entidad AssetAnnualReview a su DTO de lectura.
     *
     * @param review entidad de revision anual
     * @return DTO con datos de la revision y del activo asociado
     */
    private AnnualReviewDTO toAnnualReviewDTO(AssetAnnualReview review) {
        return AnnualReviewDTO.builder()
                .id(review.getId())
                .assetId(review.getAsset().getId())
                .assetCode(review.getAsset().getAssetCode())
                .assetName(review.getAsset().getAssetName())
                .reviewDate(review.getReviewDate())
                .fiscalYear(review.getFiscalYear())
                .previousUsefulLife(review.getPreviousUsefulLife())
                .newUsefulLife(review.getNewUsefulLife())
                .previousResidualValue(review.getPreviousResidualValue())
                .newResidualValue(review.getNewResidualValue())
                .previousDepreciationMonthly(review.getPreviousDepreciationMonthly())
                .newDepreciationMonthly(review.getNewDepreciationMonthly())
                .reviewType(review.getReviewType())
                .justification(review.getJustification())
                .reviewedBy(review.getReviewedBy())
                .createdAt(review.getCreatedAt())
                .build();
    }
}

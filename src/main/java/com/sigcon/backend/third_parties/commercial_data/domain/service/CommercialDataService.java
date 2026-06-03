package com.sigcon.backend.third_parties.commercial_data.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.parametrization.resources.application.PaymentTermsDTO;
import com.sigcon.backend.parametrization.resources.domain.model.PaymentTerms;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentTermsRepository;
import com.sigcon.backend.third_parties.commercial_data.application.CommercialDataDTO;
import com.sigcon.backend.third_parties.commercial_data.application.CommercialDataHistoryDTO;
import com.sigcon.backend.third_parties.commercial_data.application.CommercialDataRequest;
import com.sigcon.backend.third_parties.commercial_data.application.CommercialDataResponse;
import com.sigcon.backend.third_parties.commercial_data.domain.model.CommercialData;
import com.sigcon.backend.third_parties.commercial_data.domain.model.CommercialDataHistory;
import com.sigcon.backend.third_parties.commercial_data.domain.repository.CommercialDataHistoryRepository;
import com.sigcon.backend.third_parties.commercial_data.domain.repository.CommercialDataRepository;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para la gestion de datos comerciales de terceros.
 * Soporta campos de moneda (TER-11), vigencia temporal (TER-12)
 * e historial de cambios (TER-12).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CommercialDataService {
    private final CommercialDataRepository commercialDataRepository;
    private final CommercialDataHistoryRepository commercialDataHistoryRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final PaymentTermsRepository paymentTermsRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final UserUtil userUtil;
    private final AuditPublisher auditPublisher;
    /** HU-TER-12 E4 (2026-04-27): bloquea delete si hay cartera AR. */
    private final com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository salesInvoiceRepository;

   /*
     * Crear datos comerciales de un tercero.
     */
    public ResponseEntity<?> create(CommercialDataRequest request,
            org.springframework.validation.BindingResult bindingResult) {

        // 1. Validar errores de bean validation
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        // 2. Validar que el tercero exista
        ThirdParty thirdParty = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "CD_001: El tercero no existe."));

        // 3. Validar que el término de pago exista
        PaymentTerms paymentTerm = paymentTermsRepository.findById(request.getPaymentTermId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "CD_004: El término de pago no existe."));

        // 4. Validar que no exista ya un registro vigente para ese tercero
        if (commercialDataRepository.existsByThirdPartyIdAndDeletedAtIsNull(request.getThirdPartyId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("CD_002: Ya existen datos comerciales vigentes para este tercero.")));
        }

        // PT-03 (TER-RF-11): limite de credito > 0 si se informa; moneda
        // obligatoria cuando hay limite de credito.
        validateCreditLimitAndCurrency(request);

        // 5. Resolver moneda (opcional)
        CurrencyType currency = resolveCurrency(request.getCurrencyId());

        // 6. Validar coherencia de fechas de vigencia
        validateValidityDates(request.getValidityFrom(), request.getValidityTo());

        // 7. Mapear request → DTO → entity y persistir
        CommercialData entity = mapToEntity(mapToDTO(request), thirdParty, paymentTerm);
        entity.setCurrency(currency);
        entity.setValidityFrom(request.getValidityFrom());
        entity.setValidityTo(request.getValidityTo());
        CommercialData saved = commercialDataRepository.save(entity);
        auditPublisher.publishCreate(AuditModule.TER, "CommercialData", entity.getId(), "CommercialData creado id=" + entity.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Datos comerciales creados exitosamente"),
                        Optional.of(mapToResponse(saved))));
    }

    /*
     * Actualizar datos comerciales de un tercero.
     */
    public ResponseEntity<?> update(Long thirdPartyId, CommercialDataRequest request,
            org.springframework.validation.BindingResult bindingResult) {

        // 1. Validar errores de bean validation
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        // HU-TER-12 E2 (2026-04-27): justificacion minima 30 caracteres en
        // update. La HU lo exige para cambios de limite de credito + riesgo.
        String reason = request.getChangeReason();
        if (reason == null || reason.trim().length() < 30) {
            throw new IllegalArgumentException(
                    "Debe ingresar el motivo del cambio (minimo 30 caracteres).");
        }

        // 2. Validar que el tercero exista
        thirdPartyRepository.findById(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "CD_001: El tercero no existe."));

        // 3. Validar que el término de pago exista
        PaymentTerms paymentTerm = paymentTermsRepository.findById(request.getPaymentTermId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "CD_004: El término de pago no existe."));

        // 4. Buscar registro vigente
        CommercialData current = commercialDataRepository
                .findByThirdPartyIdAndDeletedAtIsNull(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        // HU-TER-12 E3.0 (Bloque AN, 2026-05-04): mensaje literal Excel.
                        "Este tercero aun no tiene condiciones comerciales registradas"));

        // PT-03 (TER-RF-11): el limite de credito, si se informa, debe ser > 0;
        // y si hay limite de credito, la moneda es obligatoria.
        validateCreditLimitAndCurrency(request);

        // 5. Resolver moneda (opcional)
        CurrencyType currency = resolveCurrency(request.getCurrencyId());

        // 6. Validar coherencia de fechas de vigencia
        validateValidityDates(request.getValidityFrom(), request.getValidityTo());

        // 7. Capturar valores previos para historial
        CommercialDataDTO dto = mapToDTO(request);
        List<CommercialDataHistory> changes = trackChanges(current, dto, paymentTerm, currency, reason.trim());

        // 8. Actualizar campos
        current.setPaymentTerm(paymentTerm);
        current.setLimitCredit(dto.getLimitCredit());
        current.setRiskLevel(dto.getRiskLevel());
        current.setCurrency(currency);
        current.setValidityFrom(request.getValidityFrom());
        current.setValidityTo(request.getValidityTo());
        CommercialData updated = commercialDataRepository.save(current);
        // PT-03 (TER-RF-12): incluir el motivo del cambio en la auditoria.
        auditPublisher.publishUpdate(AuditModule.TER, "CommercialData", current.getId(),
                "CommercialData actualizado id=" + current.getId() + " | motivo=" + reason.trim());

        // 9. Persistir historial de cambios
        if (!changes.isEmpty()) {
            commercialDataHistoryRepository.saveAll(changes);
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Datos comerciales actualizados exitosamente"),
                        Optional.of(mapToResponse(updated))));
    }

    /*
     * Consultar datos comerciales vigentes de un tercero.
     */
    public ResponseEntity<?> getByThirdParty(Long thirdPartyId) {

        CommercialData commercialData = commercialDataRepository
                .findByThirdPartyIdAndDeletedAtIsNull(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        // HU-TER-12 E3.0 (Bloque AN, 2026-05-04): mensaje literal Excel.
                        "Este tercero aun no tiene condiciones comerciales registradas"));

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.empty(),
                        Optional.of(mapToResponse(commercialData))));
    }

    /*
     * Eliminar (soft delete) datos comerciales de un tercero.
     */
    public ResponseEntity<?> delete(Long thirdPartyId, String justification) {

        // PT-10 (TER-RF-12, 2026-06-02): justificacion obligatoria (minimo 30
        // caracteres) para eliminar las condiciones comerciales.
        if (justification == null || justification.trim().length() < 30) {
            throw new IllegalArgumentException(
                    "Debe ingresar la justificacion de eliminacion de las condiciones comerciales (minimo 30 caracteres)");
        }
        String reason = justification.trim();

        CommercialData commercialData = commercialDataRepository
                .findByThirdPartyIdAndDeletedAtIsNull(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        // HU-TER-12 E3.0 (Bloque AN, 2026-05-04): mensaje literal Excel.
                        "Este tercero aun no tiene condiciones comerciales registradas"));

        // HU-TER-12 E4 (2026-04-27): bloquear si el cliente tiene cartera AR
        // activa. Antes se permitia eliminar las condiciones aunque hubiera
        // facturas de venta sin pagar; el calculo de provision quedaba sin
        // marco de referencia. Ahora bloquea y exige liquidar primero.
        long openInvoices = salesInvoiceRepository.countActiveByThirdParty(thirdPartyId);
        if (openInvoices > 0) {
            throw new IllegalArgumentException(
                    "No se pueden eliminar las condiciones comerciales: el tercero tiene "
                  + "cartera activa pendiente de cobro. Primero cancele o salde las facturas "
                  + "vigentes en Cuentas por Cobrar.");
        }

        // PT-10: dejar evidencia de la baja en el historial (estructura de
        // trazabilidad) con la justificacion ingresada.
        CommercialDataHistory deletionMark = CommercialDataHistory.builder()
                .commercialDataId(commercialData.getId())
                .fieldName("ELIMINACION")
                .oldValue("VIGENTE")
                .newValue("ELIMINADO")
                .changedBy(resolveCurrentUserIdSafe())
                .changeReason(reason)
                .build();
        commercialDataHistoryRepository.save(deletionMark);

        commercialDataRepository.delete(commercialData);
        // PT-10: la auditoria de eliminacion incluye la justificacion.
        auditPublisher.publishDelete(AuditModule.TER, "CommercialData", commercialData.getId(),
                "CommercialData eliminado id=" + commercialData.getId() + " | justificacion=" + reason);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Datos comerciales eliminados exitosamente"),
                        Optional.empty()));
    }

    /**
     * TER-12: Consultar historial de cambios de datos comerciales de un tercero.
     *
     * @param thirdPartyId ID del tercero
     * @return lista de cambios ordenados por fecha descendente
     */
    public ResponseEntity<?> getHistory(Long thirdPartyId) {
        CommercialData commercialData = commercialDataRepository
                .findByThirdPartyIdAndDeletedAtIsNull(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        // HU-TER-12 E3.0 (Bloque AN, 2026-05-04): mensaje literal Excel.
                        "Este tercero aun no tiene condiciones comerciales registradas"));

        List<CommercialDataHistoryDTO> history = commercialDataHistoryRepository
                .findByCommercialDataIdOrderByChangedAtDesc(commercialData.getId())
                .stream()
                .map(h -> CommercialDataHistoryDTO.builder()
                        .id(h.getId())
                        .commercialDataId(h.getCommercialDataId())
                        .fieldName(h.getFieldName())
                        .oldValue(h.getOldValue())
                        .newValue(h.getNewValue())
                        .changedBy(h.getChangedBy())
                        .changedAt(h.getChangedAt())
                        .changeReason(h.getChangeReason())
                        .build())
                .toList();

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Historial de cambios obtenido exitosamente"),
                        Optional.of(history)));
    }

    // =========================================================================
    // METODOS PRIVADOS
    // =========================================================================

    private CommercialDataDTO mapToDTO(CommercialDataRequest request) {
        return CommercialDataDTO.builder()
                .thirdPartyId(request.getThirdPartyId())
                .paymentTermId(request.getPaymentTermId())
                .limitCredit(request.getLimitCredit())
                .riskLevel(request.getRiskLevel())
                .currencyId(request.getCurrencyId())
                .validityFrom(request.getValidityFrom())
                .validityTo(request.getValidityTo())
                .build();
    }

    /**
     * HU-TER-11 E1.0/E5.0 (Bloque AN, 2026-05-04): porcentaje de provision
     * ECL segun nivel de riesgo (NIIF 9). LOW=1%, MEDIUM=5%, HIGH=20%.
     * Si riskLevel es null retorna null para que el frontend lo oculte.
     */
    private BigDecimal provisionPctFor(RiskSegmentation riskLevel) {
        if (riskLevel == null) return null;
        switch (riskLevel) {
            case LOW: return new BigDecimal("1.00");
            case MEDIUM: return new BigDecimal("5.00");
            case HIGH: return new BigDecimal("20.00");
            default: return null;
        }
    }

    private CommercialData mapToEntity(CommercialDataDTO dto, ThirdParty thirdParty, PaymentTerms paymentTerm) {
        return CommercialData.builder()
                .thirdParty(thirdParty)
                .paymentTerm(paymentTerm)
                .limitCredit(dto.getLimitCredit())
                .riskLevel(dto.getRiskLevel())
                .build();
    }

    private CommercialDataResponse mapToResponse(CommercialData entity) {
        CommercialDataResponse.CommercialDataResponseBuilder builder = CommercialDataResponse.builder()
                .Id(entity.getId())
                .thirdPartyId(entity.getThirdParty().getId())
                .thirdParty(ThirdPartyDTO.builder()
                        .id(entity.getThirdParty().getId())
                        .thirdPartyCode(entity.getThirdParty().getThirdPartyCode())
                        .nit(entity.getThirdParty().getNit())
                        .dv(entity.getThirdParty().getDv())
                        .businessName(entity.getThirdParty().getBusinessName())
                        .blockingReason(entity.getThirdParty().getBlockingReason())
                        .creditLimit(entity.getThirdParty().getCreditLimit())
                        .paymentTerms(entity.getThirdParty().getPaymentTerms())
                        .marketSegment(entity.getThirdParty().getMarketSegment())
                        .createdAt(entity.getThirdParty().getCreatedAt())
                        .updatedAt(entity.getThirdParty().getUpdatedAt())
                        .build())
                .paymentTerm(PaymentTermsDTO.builder()
                        .id(entity.getPaymentTerm().getId())
                        .name(entity.getPaymentTerm().getName())
                        .days(entity.getPaymentTerm().getDays())
                        .build())
                .limitCredit(entity.getLimitCredit())
                .riskLevel(entity.getRiskLevel())
                // HU-TER-11 E1.0/E5.0 (Bloque AN, 2026-05-04): % provision ECL
                // automatico segun riskLevel para que el frontend lo muestre
                // junto al nivel de riesgo sin tener que recalcularlo.
                .provisionPct(provisionPctFor(entity.getRiskLevel()))
                .validityFrom(entity.getValidityFrom())
                .validityTo(entity.getValidityTo())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        if (entity.getCurrency() != null) {
            builder.currencyId(entity.getCurrency().getId())
                    .currencyIsoCode(entity.getCurrency().getIsoCode())
                    .currencyName(entity.getCurrency().getName());
        }

        return builder.build();
    }

    /**
     * Resuelve la moneda a partir de su ID. Si el ID es null retorna null.
     */
    private CurrencyType resolveCurrency(Long currencyId) {
        if (currencyId == null) {
            return null;
        }
        return currencyTypeRepository.findByIdAndDeletedAtIsNull(currencyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "CD_005: La moneda especificada no existe."));
    }

    /**
     * Valida coherencia de fechas de vigencia: validityFrom debe ser anterior o igual a validityTo.
     */
    private void validateValidityDates(java.time.LocalDate from, java.time.LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "CD_006: La fecha de inicio de vigencia no puede ser posterior a la fecha de fin.");
        }
    }

    /**
     * TER-12: Compara valores anteriores y nuevos para generar registros de historial.
     */
    /**
     * PT-03 (TER-RF-11, 2026-06-02): valida el limite de credito y la moneda.
     * El limite no es obligatorio (decision de negocio pendiente), pero si se
     * informa debe ser mayor que cero; y cuando hay limite de credito la moneda
     * es obligatoria.
     */
    private void validateCreditLimitAndCurrency(CommercialDataRequest request) {
        BigDecimal limit = request.getLimitCredit();
        if (limit != null) {
            if (limit.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "El limite de credito debe ser mayor que cero");
            }
            if (request.getCurrencyId() == null) {
                throw new IllegalArgumentException(
                        "Debe seleccionar la moneda cuando registra un limite de credito");
            }
        }
    }

    /** Obtiene el id del usuario actual sin fallar si no hay sesion. */
    private Long resolveCurrentUserIdSafe() {
        try {
            return userUtil.getUser().getId();
        } catch (Exception e) {
            return null;
        }
    }

    private List<CommercialDataHistory> trackChanges(CommercialData current, CommercialDataDTO dto,
            PaymentTerms newPaymentTerm, CurrencyType newCurrency, String reason) {
        List<CommercialDataHistory> changes = new ArrayList<>();
        Long currentUserId = null;
        try {
            currentUserId = userUtil.getUser().getId();
        } catch (Exception e) {
            log.warn("No se pudo obtener el usuario actual para el historial de cambios comerciales");
        }

        // Comparar paymentTerm
        if (!Objects.equals(current.getPaymentTerm().getId(), newPaymentTerm.getId())) {
            changes.add(buildHistory(current.getId(), "paymentTermId",
                    String.valueOf(current.getPaymentTerm().getId()),
                    String.valueOf(newPaymentTerm.getId()), currentUserId));
        }
        // Comparar limitCredit
        if (!Objects.equals(current.getLimitCredit(), dto.getLimitCredit())) {
            changes.add(buildHistory(current.getId(), "limitCredit",
                    String.valueOf(current.getLimitCredit()),
                    String.valueOf(dto.getLimitCredit()), currentUserId));
        }
        // Comparar riskLevel
        if (!Objects.equals(current.getRiskLevel(), dto.getRiskLevel())) {
            changes.add(buildHistory(current.getId(), "riskLevel",
                    current.getRiskLevel() != null ? current.getRiskLevel().name() : null,
                    dto.getRiskLevel() != null ? dto.getRiskLevel().name() : null, currentUserId));
        }
        // Comparar currency
        Long oldCurrencyId = current.getCurrency() != null ? current.getCurrency().getId() : null;
        Long newCurrencyId = newCurrency != null ? newCurrency.getId() : null;
        if (!Objects.equals(oldCurrencyId, newCurrencyId)) {
            changes.add(buildHistory(current.getId(), "currencyId",
                    String.valueOf(oldCurrencyId), String.valueOf(newCurrencyId), currentUserId));
        }
        // Comparar validityFrom
        if (!Objects.equals(current.getValidityFrom(), dto.getValidityFrom())) {
            changes.add(buildHistory(current.getId(), "validityFrom",
                    String.valueOf(current.getValidityFrom()),
                    String.valueOf(dto.getValidityFrom()), currentUserId));
        }
        // Comparar validityTo
        if (!Objects.equals(current.getValidityTo(), dto.getValidityTo())) {
            changes.add(buildHistory(current.getId(), "validityTo",
                    String.valueOf(current.getValidityTo()),
                    String.valueOf(dto.getValidityTo()), currentUserId));
        }

        // PT-03 (TER-RF-12): persistir el motivo del cambio en cada registro.
        changes.forEach(h -> h.setChangeReason(reason));
        return changes;
    }

    private CommercialDataHistory buildHistory(Long commercialDataId, String fieldName,
            String oldValue, String newValue, Long changedBy) {
        return CommercialDataHistory.builder()
                .commercialDataId(commercialDataId)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(changedBy)
                .build();
    }
}

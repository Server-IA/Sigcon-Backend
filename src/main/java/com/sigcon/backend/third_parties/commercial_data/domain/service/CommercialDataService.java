package com.sigcon.backend.third_parties.commercial_data.domain.service;

import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.resources.application.PaymentTermsDTO;
import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.sigcon.backend.parametrization.resources.domain.model.PaymentTerms;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentTermsRepository;
import com.sigcon.backend.third_parties.commercial_data.application.CommercialDataDTO;
import com.sigcon.backend.third_parties.commercial_data.application.CommercialDataRequest;
import com.sigcon.backend.third_parties.commercial_data.application.CommercialDataResponse;
import com.sigcon.backend.third_parties.commercial_data.domain.model.CommercialData;
import com.sigcon.backend.third_parties.commercial_data.domain.repository.CommercialDataRepository;
import com.sigcon.backend.third_parties.third_parties.application.ThirdContactDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyRoleCatalogDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyStatusCatalogDTO;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CommercialDataService {
    private final CommercialDataRepository commercialDataRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final PaymentTermsRepository paymentTermsRepository; 

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

        // 5. Mapear request → DTO → entity y persistir
        CommercialData saved = commercialDataRepository.save(
                mapToEntity(mapToDTO(request), thirdParty, paymentTerm));

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
                        "CD_003: No existen datos comerciales vigentes para este tercero."));

        // 5. Actualizar campos
        CommercialDataDTO dto = mapToDTO(request);
        current.setPaymentTerm(paymentTerm);
        current.setLimitCredit(dto.getLimitCredit());
        current.setRiskLevel(dto.getRiskLevel());
        CommercialData updated = commercialDataRepository.save(current);

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
                        "CD_003: No existen datos comerciales vigentes para este tercero."));

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.empty(),
                        Optional.of(mapToResponse(commercialData))));
    }

    /*
     * Eliminar (soft delete) datos comerciales de un tercero.
     */
    public ResponseEntity<?> delete(Long thirdPartyId) {

        CommercialData commercialData = commercialDataRepository
                .findByThirdPartyIdAndDeletedAtIsNull(thirdPartyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "CD_003: No existen datos comerciales vigentes para este tercero."));

        commercialDataRepository.delete(commercialData);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Datos comerciales eliminados exitosamente"),
                        Optional.empty()));
    }

    // =========================================================================
    // MÉTODOS PRIVADOS
    // =========================================================================

    private CommercialDataDTO mapToDTO(CommercialDataRequest request) {
        return CommercialDataDTO.builder()
                .thirdPartyId(request.getThirdPartyId())
                .paymentTermId(request.getPaymentTermId())
                .limitCredit(request.getLimitCredit())
                .riskLevel(request.getRiskLevel())
                .build();
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
        return CommercialDataResponse.builder()
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
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

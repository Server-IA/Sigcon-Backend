package com.sigcon.backend.assets.niif_alerts.domain.service;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.assets.niif_alerts.domain.model.*;
import com.sigcon.backend.assets.niif_alerts.domain.repository.*;
import com.sigcon.backend.assets.niif_alerts.application.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NiifComplianceService {

    private final AssetsRepository assetsRepository;
    private final NiifParameterRepository parameterRepository;
    private final NiifVerificationRepository verificationRepository;
    private final NiifCorrectionRepository correctionRepository;

    /**
     * NIIF-RF-01
     * Verificación de cumplimiento NIIF
     */
    public NiifVerificationResponse verify(VerifyNiifRequest request){

        Assets asset = assetsRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Activo no encontrado"));

        NiifParameter parameter =
                parameterRepository.findByAssetCategory(asset.getAssetType().name())
                        .orElseThrow(() -> new RuntimeException("No hay parámetros NIIF configurados"));

        List<String> alerts = new ArrayList<>();

        // Validar método de depreciación
        if(!asset.getDepreciationMethod().name().equals(parameter.getDepreciationMethod())){
            alerts.add("Método de depreciación no permitido.");
        }

        // Validar vida útil
        int min = (int)(parameter.getStandardUsefulLife() * 0.8);
        int max = (int)(parameter.getStandardUsefulLife() * 1.2);

        if(asset.getUsefulLifeMonths() < min || asset.getUsefulLifeMonths() > max){
            alerts.add("Vida útil fuera del rango permitido por NIIF.");
        }

        // Validar deterioro
        int years = Period.between(asset.getAcquisitionDate(), LocalDate.now()).getYears();

        if(Boolean.TRUE.equals(parameter.getRequiresImpairment()) && years > 5){
            alerts.add("El activo requiere prueba de deterioro.");
        }

        String result;

        if(alerts.isEmpty()){
            result = "COMPLIANT";
        } 
        else if(alerts.size() == 1){
            result = "WARNING";
        } 
        else{
            result = "CRITICAL";
        }

        // Guardar verificación
        NiifVerification verification = NiifVerification.builder()
                .assetId(request.getAssetId())
                .result(result)
                .message(String.join(" | ", alerts))
                .verificationDate(LocalDateTime.now())
                .build();

        verificationRepository.save(verification);

        return NiifVerificationResponse.builder()
                .assetId(request.getAssetId())
                .result(result)
                .alerts(alerts)
                .build();
    }

    /**
     * NIIF-RF-02
     * Aplicar corrección NIIF
     */
    public String applyCorrection(NiifCorrectionRequest request){

        Assets asset = assetsRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Activo no encontrado"));

        NiifCorrection correction = NiifCorrection.builder()
                .assetId(request.getAssetId())
                .correctionType(request.getCorrectionType())
                .justification(request.getJustification())
                .previousValue(request.getPreviousValue())
                .newValue(request.getNewValue())
                .correctionDate(LocalDateTime.now())
                .build();

        correctionRepository.save(correction);

        return "Corrección NIIF registrada correctamente";
    }

}
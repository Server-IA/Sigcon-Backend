package com.sigcon.backend.assets.niif_alerts.domain.service;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.assets.niif_alerts.application.*;
import com.sigcon.backend.assets.niif_alerts.domain.model.enums.*;
import com.sigcon.backend.assets.niif_alerts.domain.model.*;
import com.sigcon.backend.assets.niif_alerts.domain.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NiifAlertsService {

    private final AssetsRepository assetsRepository;
    private final NiifVerificationRepository verificationRepository;
    private final NiifAlertRepository alertRepository;
    private final NiifCorrectionRepository correctionRepository;

    public List<NiifVerificationResultDTO> verifyAssets(VerifyNiifRequest request){

        List<Assets> assets = assetsRepository.findAllById(request.getAssetIds());

        List<NiifVerificationResultDTO> results = new ArrayList<>();

        for(Assets asset : assets){

            List<String> alerts = new ArrayList<>();
            NiifResult result = NiifResult.COMPLIANT;

            if(asset.getUsefulLifeMonths() == null || asset.getUsefulLifeMonths() <= 0){
                alerts.add("El activo no tiene vida útil válida");
                result = NiifResult.NON_COMPLIANT;
            }

            if(asset.getCurrentBookValue() != null &&
               asset.getCurrentBookValue().compareTo(asset.getAcquisitionValue()) > 0){
                alerts.add("El valor en libros supera el valor de adquisición");
                result = NiifResult.WARNING;
            }

            if(asset.getLastDepreciationDate() != null &&
               asset.getLastDepreciationDate().isBefore(LocalDate.now().minusMonths(12))){
                alerts.add("El activo no ha sido depreciado en más de 12 meses");
                if(result != NiifResult.NON_COMPLIANT){
                    result = NiifResult.WARNING;
                }
            }

            NiifVerification verification = verificationRepository.save(
                    NiifVerification.builder()
                            .asset(asset)
                            .result(result)
                            .summary("Verificación automática NIIF")
                            .build()
            );

            for(String msg : alerts){
                alertRepository.save(
                        NiifAlert.builder()
                                .verification(verification)
                                .severity(NiifSeverity.WARNING)
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

    public String applyCorrection(ApplyNiifCorrectionRequest request){

        Assets asset = assetsRepository.findById(request.getAssetId())
                .orElseThrow(() -> new RuntimeException("Activo no encontrado"));

        if(request.getCorrectionType() == NiifCorrectionType.USEFUL_LIFE_ADJUSTMENT){
            asset.setUsefulLifeMonths(request.getNewUsefulLifeMonths());
        }

        if(request.getCorrectionType() == NiifCorrectionType.REVALUATION){
            asset.setCurrentBookValue(request.getNewBookValue());
        }

        assetsRepository.save(asset);

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

}
package com.sigcon.backend.third_parties.ecl_segmentation.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.repository.EclSegmentationRepository;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.EclSegmentation;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.EclSegmentationHistory;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.SegmentationSource;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.repository.EclSegmentationHistoryRepository;
import com.sigcon.backend.third_parties.ecl_segmentation.application.ArDataDTO;
import com.sigcon.backend.third_parties.ecl_segmentation.application.EclSegmentationHistoryResponse;
import com.sigcon.backend.third_parties.ecl_segmentation.application.EclSegmentationResponse;
import com.sigcon.backend.third_parties.ecl_segmentation.application.ManualAdjustmentRequest;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty; // Import para validación de rol cliente activo
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository; // Import para validación de rol cliente activo

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EclSegmentationService { 
    
    private final EclSegmentationRepository eclSegmentationRepository;
    private final EclSegmentationHistoryRepository eclSegmentationHistoryRepository; 
    private final ThirdPartyRepository thirdPartyRepository;
    //si al final si se requiere datos del modulo Accounts Receivable aui se inyectara su repositorio
    //private final ArRepository arRepository;

    /*
    Terceros-RF-08 -- Flujo 1,2 y 3: Calcular y persistir Segmento automatico de un cliente.  
    */
   public ResponseEntity<?> calculateSegmentation(Long clientId, boolean isMonthlyClose) { 
        
        // Validar que el Cliente exista y tenga rol de cliente activo segun el ECL_003 del Requerimiento. Se asume que un cliente sin rol activo no puede ser segmentado, aunque tenga datos en AR.
        validateClientRole(clientId);

        //1. obtener datos AR del cliente (se utiliza el ArDTO para reemplazar al modulo hasta implementarlo)
        ArDataDTO arData = getArData(clientId);
        
        //2. Validar la disponibilidad de los datos AR segun el ECL_001 del Requerimiento. 
        if (arData == null || !Boolean.TRUE.equals(arData.getDataAvailable())){
            RiskSegmentation fallback = RiskSegmentation.PENDING; //Segmento de riesgo por defecto cuando no hay datos disponibles o son invalidos
            return persistSegment(clientId, fallback, fallback, SegmentationSource.AUTOMATIC, null, isMonthlyClose);
        }

        //3. Calcular segmentacion segun reglas de mora 
        RiskSegmentation calculatedSegmentation = calculateByOverdueDays(arData.getOverdueDays());

        //4. persistir segmentacion calculada 
        return persistSegment(clientId, calculatedSegmentation, calculatedSegmentation, SegmentationSource.AUTOMATIC, null, isMonthlyClose);
   }

   /*
   Terceros-RF-08 -- Flujo 4,5,6 y 7: Ajuste manual del segmento de riesgo de un cliente.  
   */
  public ResponseEntity<?> applyManualAdjustment(
    ManualAdjustmentRequest request, 
    org.springframework.validation.BindingResult bindingResult) {

        //1. validar errores de bean validation 
        if (bindingResult.hasErrors()) {

            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        } 

        // Validar que el Cliente exista y tenga rol de cliente activo segun el ECL_003 del Requerimiento. Se asume que un cliente sin rol activo no puede ser segmentado, aunque tenga datos en AR.
        validateClientRole(request.getClientId());

         // 2. Verificar que el cliente tiene segmento calculado previamente
        EclSegmentation current = eclSegmentationRepository
                .findByClientIdAndDeletedAtIsNull(request.getClientId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "ECL_001: No se pudo calcular segmento: datos AR no disponibles."));

        // 3. Validar que el nuevo segmento no sea PENDING — solo el sistema asigna ese valor
        if (request.getNewSegmentation() == RiskSegmentation.PENDING) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("El segmento PENDING solo puede ser asignado por el sistema.")));
        }

        // 4. Registrar en histórico antes de modificar
        saveHistory(
                current.getClientId(),
                current.getFinalSegment(),
                request.getNewSegmentation(),
                SegmentationSource.MANUAL,
                request.getJustification()
        );

        // 5. Actualizar segmento — el ajuste manual prevalece hasta el próximo recálculo
        current.setFinalSegment(request.getNewSegmentation());
        current.setSegmentationSource(SegmentationSource.MANUAL);
        current.setJustification(request.getJustification());
        current.setCalculationDate(LocalDateTime.now());
        eclSegmentationRepository.save(current);

        // 6. Mapear y retornar respuesta
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Segmento actualizado exitosamente"),
                        Optional.of(mapToResponse(current))
                )
        ); 
  }
   /**
     * RF08 — Consultar segmento vigente de un cliente.
     */
    public ResponseEntity<?> getSegmentByClient(Long clientId) {

        EclSegmentation segmentation = eclSegmentationRepository
                .findByClientIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ECL_001: No se pudo calcular segmento: datos AR no disponibles."));

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.empty(),
                        Optional.of(mapToResponse(segmentation))
                )
        );
    }

    /**
     * RF08 — HU Caso 4: Exportar lista de clientes segmentados para cierre NIIF/GL.
     */
    public ResponseEntity<?> getAllSegmentsForEcl(com.sigcon.backend.utils.DataTableRequest request) {

        if (request == null) {
            request = new com.sigcon.backend.utils.DataTableRequest();
        }

        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 10 : length;
        int page = start / safeLength;

        org.springframework.data.domain.Pageable pageable = length == -1
                ? org.springframework.data.domain.Pageable.unpaged()
                : org.springframework.data.domain.PageRequest.of(page, safeLength);

        org.springframework.data.jpa.domain.Specification<EclSegmentation> spec =
                new com.sigcon.backend.utils.DataTableSpecificationBuilder<EclSegmentation>()
                        .build(request)
                        .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        org.springframework.data.domain.Page<EclSegmentation> segments =
                eclSegmentationRepository.findAll(spec, pageable);

        return ResponseEntity.ok(
                com.sigcon.backend.utils.DataTableResponse.from(
                        segments.map(this::mapToResponse),
                        request.getDraw()
                )
        );
    }

    /**
     * RF08 — Consultar histórico de cambios de segmento de un cliente.
     */
    public ResponseEntity<?> getSegmentHistory(Long clientId) {

        List<EclSegmentationHistoryResponse> history = eclSegmentationHistoryRepository
                .findByClientIdOrderByChangeDateDesc(clientId)
                .stream()
                .map(this::mapToHistoryResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.empty(),
                        Optional.of(history)
                )
        );
    }

    // =========================================================================
    // MÉTODOS PRIVADOS
    // =========================================================================

    /**
        * Validar que el cliente exista y tenga rol de cliente activo segun el ECL_003 del Requerimiento. 
        */
       private void validateClientRole(Long clientId){
        ThirdParty thirdParty = thirdPartyRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ECL_003: Cliente no tiene Rol Cliente activo."
                ));
        boolean hasClientRole = thirdParty.getRoles().stream()
                .anyMatch(role -> "CLIENTE".equalsIgnoreCase(role.getName())); 
        if (!hasClientRole) {
                throw new IllegalArgumentException(
                        "ECL_003: Cliente no tiene Rol Cliente activo."
                ); 
        }
       }

    /**
     * Reglas automáticas de segmentación por días mora (RF08).
     * HU caso 1: >90 días → ALTO (prevalece sobre regla base >60)
     * Regla base: 0-30 BAJO | 31-60 MEDIO | >60 ALTO
     * HU caso 3: sin historial (overdueDays null) → MEDIO por defecto
     */
    private RiskSegmentation calculateByOverdueDays(Integer overdueDays) {
        if (overdueDays == null) {
            return RiskSegmentation.MEDIUM; // Cliente nuevo sin historial en AR
        }
        if (overdueDays <= 30) {
            return RiskSegmentation.LOW;
        } else if (overdueDays <= 60) {
            return RiskSegmentation.MEDIUM;
        } else {
            return RiskSegmentation.HIGH; // Incluye el caso >90 días de la HU
        }
    }

    /**
     * Persistir o actualizar segmento de un cliente.
     * Si ya existe un registro vigente, lo actualiza.
     * Si no existe, crea uno nuevo.
     */
    private ResponseEntity<?> persistSegment(
            Long clientId,
            RiskSegmentation autoSegment,
            RiskSegmentation finalSegment,
            SegmentationSource source,
            String justification, 
            boolean isMonthlyClose) {

        Optional<EclSegmentation> existing =
                eclSegmentationRepository.findByClientIdAndDeletedAtIsNull(clientId);

        EclSegmentation segmentation;

        if (existing.isPresent()) {
            // Actualizar registro existente y guardar histórico del cambio
            EclSegmentation current = existing.get();

            if (current.getSegmentationSource() == SegmentationSource.MANUAL
                    && source == SegmentationSource.AUTOMATIC
                    && !isMonthlyClose) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Existe un ajuste manual vigente. " +
                                        "El segmento solo puede recalcularse en el cierre mensual.")));
            }

            saveHistory(
                    clientId,
                    current.getFinalSegment(),
                    finalSegment,
                    source,
                    justification
            );

            current.setAutoSegment(autoSegment);
            current.setFinalSegment(finalSegment);
            current.setSegmentationSource(source);
            current.setJustification(justification);
            current.setCalculationDate(LocalDateTime.now());
            segmentation = eclSegmentationRepository.save(current);

        } else {
            // Crear nuevo registro — primer cálculo del cliente
            segmentation = eclSegmentationRepository.save(
                    EclSegmentation.builder()
                            .clientId(clientId)
                            .autoSegment(autoSegment)
                            .finalSegment(finalSegment)
                            .segmentationSource(source)
                            .justification(justification)
                            .calculationDate(LocalDateTime.now())
                            .build()
            );
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Segmento calculado y almacenado exitosamente"),
                        Optional.of(mapToResponse(segmentation))
                ));
    }

    /**
     * Guardar registro en el histórico de cambios.
     */
    private void saveHistory(
            Long clientId,
            RiskSegmentation previousSegment,
            RiskSegmentation newSegment,
            SegmentationSource source,
            String justification) {

        eclSegmentationHistoryRepository.save(
                EclSegmentationHistory.builder()
                        .clientId(clientId)
                        .previousSegment(previousSegment)
                        .newSegment(newSegment)
                        .segmentationSource(source)
                        .justification(justification)
                        .build()
        );
    }

    /**
     * Placeholder — reemplazar por llamada real al repositorio AR cuando exista el módulo.
     * Por ahora retorna datos simulados para no bloquear el desarrollo.
     * return arRepository.findLatestByClientId(clientId)
     *         .map(ar -> ArDataDTO.builder()
     *                 .clientId(clientId)
     *                 .overdueDays(ar.getOverdueDays())
     *                 .overdueAmount(ar.getOverdueAmount())
     *                 .dataAvailable(true)
     *                 .build())
     *         .orElse(ArDataDTO.builder().clientId(clientId).dataAvailable(false).build());
     */
    private ArDataDTO getArData(Long clientId) {
        // Stub: dataAvailable=false simula que el módulo AR aún no existe
        return ArDataDTO.builder()
                .clientId(clientId)
                .dataAvailable(false)
                .build();
    }

    /**
     * Mapear EclSegmentation a EclSegmentationResponse.
     */
    private EclSegmentationResponse mapToResponse(EclSegmentation segmentation) {
        return EclSegmentationResponse.builder()
                .id(segmentation.getId())
                .clientId(segmentation.getClientId())
                .autoSegment(segmentation.getAutoSegment())
                .finalSegment(segmentation.getFinalSegment())
                .segmentationSource(segmentation.getSegmentationSource())
                .justification(segmentation.getJustification())
                .calculationDate(segmentation.getCalculationDate())
                .createdAt(segmentation.getCreatedAt())
                .updatedAt(segmentation.getUpdatedAt())
                .build();
    }

    /**
     * Mapear EclSegmentationHistory a EclSegmentationHistoryResponse.
     */
    private EclSegmentationHistoryResponse mapToHistoryResponse(EclSegmentationHistory history) {
        return EclSegmentationHistoryResponse.builder()
                .id(history.getId())
                .clientId(history.getClientId())
                .previousSegment(history.getPreviousSegment())
                .newSegment(history.getNewSegment())
                .segmentationSource(history.getSegmentationSource())
                .justification(history.getJustification())
                .changeDate(history.getChangeDate())
                .build();
    }
}

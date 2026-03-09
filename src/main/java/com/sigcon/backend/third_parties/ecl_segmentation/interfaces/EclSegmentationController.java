package com.sigcon.backend.third_parties.ecl_segmentation.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.sigcon.backend.third_parties.ecl_segmentation.application.ManualAdjustmentRequest;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.service.EclSegmentationService;
import com.sigcon.backend.utils.DataTableRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ecl-segmentation")
@RequiredArgsConstructor
public class EclSegmentationController { 

    private final EclSegmentationService eclSegmentationService; 

     /**
     * RF08 — Flujo 1,2,3: Calcular segmento automático de un cliente.
     * POST /api/v1/ecl-segmentation/calculate/{clientId}
     */
    @PostMapping("/calculate/{clientId}")
    @PreAuthorize("hasAuthority('PERM_CALCULATE_ECL_SEGMENT') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> calculateSegment(@PathVariable Long clientId, 
        @RequestParam(defaultValue = "false") boolean isMonthlyClose) {
        return eclSegmentationService.calculateSegmentation(clientId, isMonthlyClose);
    }

    /**
     * RF08 — Flujo 4,5,6,7: Ajuste manual del segmento con justificación.
     * PUT /api/v1/ecl-segmentation/adjust
     */ 
    @PutMapping("/adjust")
    @PreAuthorize("hasAuthority('PERM_ADJUST_ECL_SEGMENT') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> applyManualAdjustment(
            @Valid @RequestBody ManualAdjustmentRequest request,
            BindingResult bindingResult) {
        return eclSegmentationService.applyManualAdjustment(request, bindingResult);
    }

    /**
     * RF08 — Consultar segmento vigente de un cliente.
     * GET /api/v1/ecl-segmentation/{clientId}
     */
    @GetMapping("/{clientId}")
    @PreAuthorize("hasAuthority('PERM_VIEW_ECL_SEGMENT') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> getSegmentByClient(@PathVariable Long clientId) {
        return eclSegmentationService.getSegmentByClient(clientId);
    }

    /**
     * RF08 — HU Caso 4: Exportar lista paginada de clientes segmentados para cierre NIIF/GL.
     * POST /api/v1/ecl-segmentation/search
     */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_ECL_SEGMENT') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> getAllSegmentsForEcl(
            @RequestBody(required = false) DataTableRequest dtRequest) {
        return eclSegmentationService.getAllSegmentsForEcl(dtRequest);
    }

    /**
     * RF08 — Consultar histórico de cambios de segmento de un cliente.
     * GET /api/v1/ecl-segmentation/history/{clientId}
     */
    @GetMapping("/history/{clientId}")
    @PreAuthorize("hasAuthority('PERM_VIEW_ECL_SEGMENT') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> getSegmentHistory(@PathVariable Long clientId) {
        return eclSegmentationService.getSegmentHistory(clientId);
    }
}

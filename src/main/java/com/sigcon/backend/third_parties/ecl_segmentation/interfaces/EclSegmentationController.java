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
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.sigcon.backend.third_parties.ecl_segmentation.application.CalculateSegmentationRequest;
import com.sigcon.backend.third_parties.ecl_segmentation.application.ManualAdjustmentRequest;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.service.EclSegmentationService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ecl-segmentation")
@RequiredArgsConstructor
@Tag(name = "3. Módulo de Terceros - Segmentación de riesgo ECL", description = "Endpoints para la gestión de segmentación de riesgo ECL")
public class EclSegmentationController {

        private final EclSegmentationService eclSegmentationService;

        /**
         * RF08 — Flujo 1,2,3: Calcular segmento automático de un cliente.
         * POST /api/v1/ecl-segmentation/calculate/{clientId}
         */
        @Operation(summary = "Calcular segmento automatico de un cliente.", description = "RF08 Flujos 1,2,3 — Calcula y persiste el segmento de riesgo ECL de un cliente según reglas de mora. "
                        +
                        "Si los datos AR no están disponibles, asigna PENDING (ECL_001). " +
                        "Si existe un ajuste manual vigente y no es cierre mensual, retorna HTTP 409.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Segmento calculado y almacenado exitosamente. "),
                        @ApiResponse(responseCode = "409", description = "Existe un ajuste manual vigente. El segmento solo puede recalcularse en el cierre mensual"),
                        @ApiResponse(responseCode = "400", description = "ECL_003: Cliente no tiene rol CLIENTE activo")
        })
        @PostMapping("/calculate")
        @PreAuthorize("hasAuthority('PERM_CALCULATE_ECL_SEGMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> calculateSegment(@Valid @RequestBody CalculateSegmentationRequest request,
                        BindingResult bindingResult) {
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }
                return eclSegmentationService.calculateSegmentation(
                                request.getClientId(),
                                request.isMonthlyClose());
        }

        /**
         * RF08 — Flujo 4,5,6,7: Ajuste manual del segmento con justificación.
         * PUT /api/v1/ecl-segmentation/adjust
         */
        @Operation(summary = "Ajuste manual del segmento de riesgo", description = "RF08 Flujos 4,5,6,7 — Permite a un contador o analista de riesgos modificar manualmente el segmento de riesgo ECL de un cliente. "
                        +
                        "Requiere justificación de mínimo 50 caracteres (ECL_002). El ajuste prevalece hasta el próximo recálculo mensual. ")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Segmento actualizado exitosamente. "),
                        @ApiResponse(responseCode = "422", description = "ECL_002: Justificación insuficiente. "),
                        @ApiResponse(responseCode = "400", description = "ECL_003: Cliente no tiene rol CLIENTE activo"),
                        @ApiResponse(responseCode = "404", description = "ECL_001: No existe segmento calculado previo para este cliente. ")
        })
        @PutMapping("/adjust/{clientId}")
        @PreAuthorize("hasAuthority('PERM_ADJUST_ECL_SEGMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> applyManualAdjustment(
                        @PathVariable Long clientId,
                        @Valid @RequestBody ManualAdjustmentRequest request,
                        BindingResult bindingResult) {
                return eclSegmentationService.applyManualAdjustment(clientId, request, bindingResult);
        }

        /**
         * RF08 — Consultar segmento vigente de un cliente.
         * GET /api/v1/ecl-segmentation/{clientId}
         */
        @Operation(summary = "Consultar segmento vigente de un cliente", description = "RF08 — Retorna el segmento de riesgo ECL vigente de un cliente, incluyendo si fue calculado automáticamente o ajustado manualmente. ")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Segmento vigente retornado exitosamente"),
                        @ApiResponse(responseCode = "404", description = "ECL_001: No existe segmento calculado para este cliente")
        })
        @GetMapping("/{clientId}")
        @PreAuthorize("hasAuthority('PERM_VIEW_ECL_SEGMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> getSegmentByClient(@PathVariable Long clientId) {
                return eclSegmentationService.getSegmentByClient(clientId);
        }

        /**
         * RF08 — HU Caso 4: Exportar lista paginada de clientes segmentados para cierre
         * NIIF/GL.
         * POST /api/v1/ecl-segmentation/search
         */
        @Operation(summary = "Exportar lista paginada de clientes segmentados para cierre NIIF/GL. ", description = "RF08 HU Caso 4 — Retorna la lista paginada de todos los clientes con su segmento de riesgo ECL vigente. "
                        +
                        "Usado por GL para el cierre mensual NIIF. ")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista de segmentos retornada exitosamente. "),
        })
        @PostMapping("/search")
        @PreAuthorize("hasAuthority('PERM_VIEW_ECL_SEGMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> getAllSegmentsForEcl(
                        @RequestBody(required = false) DataTableRequest dtRequest) {
                return eclSegmentationService.getAllSegmentsForEcl(dtRequest);
        }

        /**
         * RF08 — Consultar histórico de cambios de segmento de un cliente.
         * GET /api/v1/ecl-segmentation/history/{clientId}
         */
        @Operation(summary = "Consultar histórico de cambios de segmento de un cliente", description = "RF08 — Retorna el histórico completo de cambios de segmento de riesgo ECL de un cliente, ordenado de más reciente a más antiguo.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Histórico retornado exitosamente")
        })
        @GetMapping("/history/{clientId}")
        @PreAuthorize("hasAuthority('PERM_VIEW_ECL_SEGMENT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> getSegmentHistory(@PathVariable Long clientId) {
                return eclSegmentationService.getSegmentHistory(clientId);
        }
}

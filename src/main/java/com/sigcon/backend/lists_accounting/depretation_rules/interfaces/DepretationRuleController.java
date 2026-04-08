package com.sigcon.backend.lists_accounting.depretation_rules.interfaces;

import com.sigcon.backend.lists_accounting.depretation_rules.application.CreateDepretationRuleRequest;
import com.sigcon.backend.lists_accounting.depretation_rules.application.UpdateDepretationRuleRequest;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.service.DepretationRuleService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/depreciation-rules")
@RequiredArgsConstructor
@Tag(name = "2. Módulo de Listas Contables - Reglas de depreciación", description = "Endpoints para la gestión de reglas de depreciación: búsqueda paginada, creación, actualización y eliminación lógica.")
public class DepretationRuleController {

    private final DepretationRuleService depretationRuleService;

    /**
     * CFG-RF-14: Consultar reglas de depreciación existentes (HU-14)
     * POST /api/v1/depretation-rules/search
     */
    @Operation(summary = "Consultar reglas de depresación", description = "Retrona un listado paginado de reglas de depreciacion segun las especificaciones del requerimiento CFG-RF-14. "
            +
            "Requiere permisos PERM_VIEW_DEPRETATION_RULE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente"),
            @ApiResponse(responseCode = "404", description = "No se encontraron reglas con esos criterios"),
            @ApiResponse(responseCode = "400", description = "Error en los parametros de busqueda")
    })
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_DEPRECIATION_RULE') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> getDepretationRules(
            @RequestBody(required = false) DataTableRequest dtRequest) {
        // try {
        return depretationRuleService.getDepretationRulesPaged(dtRequest);
        // } catch (Exception e) {
        // return ResponseEntity.badRequest()
        // .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    /**
     * CFG-RF-13: Crear nueva regla de depreciación (HU-13)
     * POST /api/v1/depretation-rules/store
     */
    @Operation(summary = "Crear reglas de depresación", description = "Crea una nueva regla de depreciacion segun las especificaciones del requerimiento CFG-RF-13. "
            +
            "Requiere permisos PERM_CREATE_DEPRETATION_RULE")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Regla creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "La cuenta contable no existe"),
            @ApiResponse(responseCode = "409", description = "Regla duplicada: ya existe una con el mismo método, cuenta y vigencia"),
            @ApiResponse(responseCode = "500", description = "Error interno al guardar la regla")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_DEPRECIATION_RULE') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> createDepretationRule(
            @Valid @RequestBody CreateDepretationRuleRequest request,
            BindingResult bindingResult) {
        // try{
        return depretationRuleService.createDepretationRule(request, bindingResult);
        // } catch (Exception e) {
        // return ResponseEntity.badRequest()
        // .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    /**
     * CFG-RF-15: Editar regla de depreciación (HU-15)
     * PUT /api/v1/depretation-rules/update
     */
    @Operation(summary = "Editar reglas de depresación Existentes", description = "Actualizar los campos editables de una regla de depreciacion segun las especificaciones del requerimiento CFG-RF-15. "
            +
            "Requiere permisos PERM_UPDATE_DEPRETATION_RULE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Regla actualizada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, regla eliminada o tasa fuera de rango"),
            @ApiResponse(responseCode = "500", description = "Error interno al guardar los cambios")
    })
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('PERM_UPDATE_DEPRECIATION_RULE') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> updateDepretationRule(
            @Valid @RequestBody UpdateDepretationRuleRequest request,
            BindingResult bindingResult) {
        // try{
        return depretationRuleService.updateDepretationRule(request, bindingResult);
        // } catch (Exception e) {
        // return ResponseEntity.badRequest()
        // .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    /**
     * CFG-RF-16: Eliminar regla de depreciación (HU-16)
     * DELETE /api/v1/depretation-rules/delete/{id}
     */
    @Operation(summary = "Eliminar regla de depresación (eliminado logico)", description = "Eliminar una regla de depreciacion segun las especificaciones del requerimiento CFG-RF-16. "
            +
            "Requiere permisos PERM_DELETE_DEPRETATION_RULE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Regla eliminada correctamente"),
            @ApiResponse(responseCode = "400", description = "Regla no encontrada, ya eliminada, o motivo vacío"),
            @ApiResponse(responseCode = "500", description = "Error interno al eliminar la regla")
    })
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_DEPRECIATION_RULE') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> deleteDepretationRule(
            @PathVariable Long id,
            @RequestParam(name = "reason", required = true) String reason) {

        return depretationRuleService.deleteDepretationRule(id, reason);
    }
}

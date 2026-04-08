package com.sigcon.backend.lists_accounting.cost_centers.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.cost_centers.domain.service.CostCenterService;
import com.sigcon.backend.utils.DataTableRequest;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/cost-centers")
@RequiredArgsConstructor
@Tag(name = "2. Módulo de Listas Contables - Centros de costo", description = "Endpoints para la gestión de centros de costo: búsqueda paginada, creación, actualización y eliminación lógica.")
public class CostCenterController {

        private final CostCenterService costCenterService;

        // =========================================================
        // PA-RF-CC-01: Listar / buscar centros de costo (paginado)
        // =========================================================
        @Operation(summary = "Buscar centros de costo (DataTable)", description = "Retorna una lista paginada de centros de costo activos (sin eliminación lógica). "
                        +
                        "Soporta filtros dinámicos y ordenamiento para integrarse con DataTables. " +
                        "Requiere el permiso **PERM_VIEW_COST_CENTERS**.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Parámetros de paginación y filtros opcionales. Si el body está vacío se devuelven todos los registros con valores por defecto.", required = false, content = @Content(mediaType = "application/json", schema = @Schema(implementation = DataTableRequest.class), examples = @ExampleObject(name = "Búsqueda con filtro por nombre", value = "{\n  \"draw\": 1,\n  \"start\": 0,\n  \"length\": 10,\n  \"search\": { \"value\": \"produccion\", \"regex\": false },\n  \"columns\": [],\n  \"order\": []\n}"))))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Lista paginada obtenida correctamente", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n  \"draw\": 1,\n  \"recordsTotal\": 25,\n  \"recordsFiltered\": 3,\n  \"data\": []\n}"))),
                        @ApiResponse(responseCode = "400", description = "Error en los parámetros de la solicitud", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
                        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_VIEW_COST_CENTERS", content = @Content)
        })
        @PostMapping("/search")
        @PreAuthorize("hasAuthority('PERM_VIEW_COST_CENTERS') or hasAuthority('ROLE_SUPERADMIN')")
        public ResponseEntity<?> searchCostCenters(@RequestBody(required = false) DataTableRequest request) {
                if (request == null) {
                        request = new DataTableRequest();
                }
                return costCenterService.getCostCentersPaged(request);
        }

        // =========================================================
        // PA-RF-CC-02: Crear centro de costo
        // =========================================================
        @Operation(summary = "Crear un centro de costo", description = "Crea un nuevo centro de costo. "
                        +
                        "El código debe ser único por empresa. " +
                        "Requiere el permiso **PERM_CREATE_COST_CENTER**.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos del centro de costo a crear. Los campos `code`, `name` y `companyId` son obligatorios.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = CostCenter.class), examples = @ExampleObject(name = "Ejemplo básico", value = "{\n  \"code\": \"CC-001\",\n  \"name\": \"Centro de Producción\",\n  \"description\": \"Centro de costo para el área de producción\",\n  \"status\": \"ACTIVE\",\n  \"companyId\": 1\n}"))))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Centro de costo creado correctamente", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n  \"title\": \"OK\",\n  \"message\": \"Centro de costo creado correctamente\",\n  \"data\": {}\n}"))),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos o código duplicado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
                        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_CREATE_COST_CENTER", content = @Content)
        })
        @PostMapping("/store")
        @PreAuthorize("hasAuthority('PERM_CREATE_COST_CENTER') or hasAuthority('ROLE_SUPERADMIN')")
        public ResponseEntity<?> storeCostCenter(@Valid @RequestBody CostCenterDTO costCenterDTO,
                        BindingResult bindingResult) {
                return costCenterService.storeCostCenter(costCenterDTO, bindingResult);
        }

        // =========================================================
        // PA-RF-CC-03: Actualizar centro de costo
        // =========================================================
        @Operation(summary = "Actualizar un centro de costo", description = "Actualiza los datos de un centro de costo existente identificado por su `id`. "
                        +
                        "No se puede actualizar un centro de costo eliminado lógicamente. " +
                        "Requiere el permiso **PERM_UPDATE_COST_CENTER**.", parameters = @Parameter(name = "id", description = "ID único del centro de costo a actualizar", required = true, example = "5"), requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos actualizados del centro de costo.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = CostCenter.class), examples = @ExampleObject(name = "Actualizar nombre y descripción", value = "{\n  \"code\": \"CC-001\",\n  \"name\": \"Centro de Producción Actualizado\",\n  \"description\": \"Nueva descripción del centro de costo\",\n  \"status\": \"ACTIVE\",\n  \"companyId\": 1\n}"))))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Centro de costo actualizado correctamente", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n  \"title\": \"OK\",\n  \"message\": \"Centro de costo actualizado correctamente\",\n  \"data\": {}\n}"))),
                        @ApiResponse(responseCode = "400", description = "Datos inválidos, código duplicado o centro eliminado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
                        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_UPDATE_COST_CENTER", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Centro de costo no encontrado", content = @Content)
        })
        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_UPDATE_COST_CENTER') or hasAuthority('ROLE_SUPERADMIN')")
        public ResponseEntity<?> updateCostCenter(
                        @PathVariable Long id,
                        @Valid @RequestBody CostCenter costCenter,
                        BindingResult bindingResult) {
                return costCenterService.updateCostCenter(id, costCenter, bindingResult);
        }

        // =========================================================
        // PA-RF-CC-04: Eliminar centro de costo (eliminación lógica)
        // =========================================================
        @Operation(summary = "Eliminar un centro de costo (eliminación lógica)", description = "Realiza una eliminación lógica del centro de costo, marcando el campo `deletedAt` con la fecha actual. "
                        +
                        "El registro permanece en la base de datos pero no aparece en las búsquedas. " +
                        "Se puede proporcionar un motivo de eliminación opcional. " +
                        "Requiere el permiso **PERM_DELETE_COST_CENTER**.", parameters = {
                                        @Parameter(name = "id", description = "ID único del centro de costo a eliminar", required = true, example = "5"),
                                        @Parameter(name = "reason", description = "Motivo opcional de la eliminación (se guarda en `deletionReason`)", required = false, example = "Centro de costo fusionado con CC-002")
                        })
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Centro de costo eliminado correctamente", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\n  \"title\": \"OK\",\n  \"message\": \"Centro de costo eliminado correctamente\"\n}"))),
                        @ApiResponse(responseCode = "400", description = "Centro de costo no encontrado o ya eliminado", content = @Content),
                        @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
                        @ApiResponse(responseCode = "403", description = "Sin permiso PERM_DELETE_COST_CENTER", content = @Content)
        })
        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_DELETE_COST_CENTER') or hasAuthority('ROLE_SUPERADMIN')")
        public ResponseEntity<?> deleteCostCenter(
                        @PathVariable Long id,
                        @RequestParam(required = false) String reason) {
                return costCenterService.deleteCostCenter(id, reason);
        }
}

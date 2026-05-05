package com.sigcon.backend.third_parties.third_parties.interfaces;

import com.sigcon.backend.third_parties.change_history.domain.service.ThirdPartyChangeHistoryService;
import com.sigcon.backend.third_parties.third_parties.application.BulkThirdPartyUploadRequest;
import com.sigcon.backend.third_parties.third_parties.application.DeleteThirdPartyRequest;
import com.sigcon.backend.third_parties.third_parties.application.RoleAssignmentDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;
import com.sigcon.backend.third_parties.third_parties.application.UpdateThirdPartyRolesStatusRequest;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRoleAssignmentRepository;
import com.sigcon.backend.third_parties.third_parties.domain.service.ThirdPartyService;
import com.sigcon.backend.third_parties.third_parties.domain.service.ThirdPartyExportService;
import com.sigcon.backend.utils.DataTableRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/third-parties")
@RequiredArgsConstructor
@Tag(name = "3. Módulo de Terceros - Terceros", description = "Endpoints para gestion de terceros")
@SecurityRequirement(name = "bearerAuth")
public class ThirdPartyController {

        private final ThirdPartyService thirdPartyService;
        private final ThirdPartyChangeHistoryService changeHistoryService;
        private final ThirdPartyRoleAssignmentRepository roleAssignmentRepository;
        private final ThirdPartyExportService exportService;

        @PostMapping("/store")
        @Operation(summary = "Registrar tercero", description = "RF02 - Crea un tercero con datos generales, fiscales y comerciales.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Payload de creacion del tercero", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThirdPartyDTO.class), examples = @ExampleObject(value = "{\n  \"nit\": \"9001234567\",\n  \"dv\": \"1\",\n  \"businessName\": \"EMPRESA ABC SAS\",\n  \"roleIds\": [1,2],\n  \"statusId\": 1,\n  \"municipalityId\": 1,\n  \"typeOrganizationId\": 1,\n  \"typeRegimenId\": 2,\n  \"withholdingIds\": [1,3],\n  \"creditLimit\": 10000000,\n  \"paymentTerms\": \"30 dias\",\n  \"marketSegment\": \"CORPORATIVO\",\n  \"contacts\": [\n    {\n      \"position\": \"Contador\",\n      \"phone\": \"3001234567\",\n      \"email\": \"contabilidad@empresa.com\",\n      \"contactPerson\": \"Pedro Perez\"\n    }\n  ]\n}"))))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Tercero registrado correctamente"),
                        @ApiResponse(responseCode = "400", description = "Error de validacion"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_CREATE_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> store(@Valid @RequestBody ThirdPartyDTO request, BindingResult bindingResult) {
                return thirdPartyService.create(request, bindingResult);
        }

        @PostMapping("/bulk/store")
        @Operation(summary = "Carga masiva de terceros", description = "RF07 - Importa terceros desde archivo CSV/XLSX enviado en base64. "
                        +
                        "No permite estado BLOQUEADO en cargue masivo.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Nombre del archivo, contenido base64, delimitador (si es CSV) y overwrite S/N.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkThirdPartyUploadRequest.class), examples = @ExampleObject(value = "{\n  \"fileName\": \"terceros.csv\",\n  \"fileBase64\": \"<BASE64_ARCHIVO>\",\n  \"delimiter\": \",\",\n  \"overwrite\": \"N\"\n}"))))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Carga masiva procesada correctamente"),
                        @ApiResponse(responseCode = "400", description = "Archivo invalido o error en alguna fila"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_BULK_STORE_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> bulkStore(@Valid @RequestBody BulkThirdPartyUploadRequest request,
                        BindingResult bindingResult) {
                return thirdPartyService.bulkStore(request, bindingResult);
        }

        @PostMapping("/search")
        @Operation(summary = "Consultar terceros", description = "RF01 - Consulta paginada con filtros avanzados (DataTable).", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, description = "Objeto DataTableRequest con filtros, orden y paginacion.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DataTableRequest.class), examples = @ExampleObject(value = "{\n  \"draw\": 1,\n  \"start\": 0,\n  \"length\": 10,\n  \"search\": { \"value\": \"900123\", \"regex\": false },\n  \"columns\": [],\n  \"order\": []\n}"))))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
                        @ApiResponse(responseCode = "400", description = "Parametros invalidos"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos"),
                        @ApiResponse(responseCode = "404", description = "Sin resultados")
        })
        @PreAuthorize("hasAuthority('PERM_VIEW_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
                return thirdPartyService.findAllPaged(request);
        }

        @GetMapping("/{id}")
        @Operation(summary = "Detalle de tercero", description = "RF01 - Retorna la ficha detallada del tercero por ID.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente"),
                        @ApiResponse(responseCode = "404", description = "Tercero no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_VIEW_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> detail(@PathVariable Long id) {
                return thirdPartyService.getDetail(id);
        }

        @GetMapping("/roles")
        @Operation(summary = "Catalogo de roles de terceros", description = "Retorna el catalogo simple de roles disponibles para terceros.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Catalogo obtenido correctamente"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_VIEW_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> rolesCatalog() {
                return thirdPartyService.getRolesCatalog();
        }

        @GetMapping("/statuses")
        @Operation(summary = "Catalogo de estados de terceros", description = "Retorna el catalogo simple de estados disponibles para terceros.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Catalogo obtenido correctamente"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_VIEW_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> statusesCatalog() {
                return thirdPartyService.getStatusesCatalog();
        }

        @PutMapping("/{id}")
        @Operation(summary = "Actualizar tercero", description = "RF03 - Actualiza la informacion de un tercero existente.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ThirdPartyDTO.class), examples = @ExampleObject(value = "{\n  \"businessName\": \"EMPRESA ABC SAS ACTUALIZADA\",\n  \"municipalityId\": 2,\n  \"withholdingIds\": [2],\n  \"contacts\": [\n    {\n      \"position\": \"Tesoreria\",\n      \"phone\": \"3007654321\",\n      \"email\": \"tesoreria@empresa.com\",\n      \"contactPerson\": \"Ana Ruiz\"\n    }\n  ]\n}"))))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Tercero actualizado correctamente"),
                        @ApiResponse(responseCode = "400", description = "Error de validacion"),
                        @ApiResponse(responseCode = "404", description = "Tercero no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_UPDATE_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> update(
                        @PathVariable Long id,
                        @Valid @RequestBody ThirdPartyDTO request,
                        BindingResult bindingResult) {
                return thirdPartyService.update(id, request, bindingResult);
        }

        @PutMapping("/{id}/roles-status")
        @Operation(summary = "Gestionar roles y estado", description = "RF04 - Actualiza los roles y estado del tercero. "
                        +
                        "Si el estado es BLOQUEADO, requiere blockingReason >= 20.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateThirdPartyRolesStatusRequest.class), examples = @ExampleObject(value = "{\n  \"roleIds\": [1,2],\n  \"statusId\": 2,\n  \"blockingReason\": \"Bloqueado por incumplimiento en validacion documental\"\n}"))))
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Roles y estado actualizados correctamente"),
                        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio"),
                        @ApiResponse(responseCode = "404", description = "Tercero no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_MANAGE_THIRD_PARTY_ROLES_STATUS') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> updateRolesStatus(
                        @PathVariable Long id,
                        @Valid @RequestBody UpdateThirdPartyRolesStatusRequest request,
                        BindingResult bindingResult) {
                return thirdPartyService.updateRolesAndStatus(id, request, bindingResult);
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "Eliminar tercero", description = "TER-10: Eliminacion logica con justificacion obligatoria (min 50 caracteres) y validacion de dependencias.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Tercero eliminado correctamente"),
                        @ApiResponse(responseCode = "400", description = "Dependencias activas o justificacion invalida"),
                        @ApiResponse(responseCode = "404", description = "Tercero no encontrado"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_DELETE_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> delete(
                        @PathVariable Long id,
                        @Valid @RequestBody DeleteThirdPartyRequest request,
                        BindingResult bindingResult) {
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(
                                        com.sigcon.backend.utils.ErrorRespondJson.getErrorRespondJson(bindingResult));
                }
                return thirdPartyService.delete(id, request);
        }

        @GetMapping("/{id}/history")
        @Operation(summary = "Historial de cambios", description = "TER-03: Consulta el historial de modificaciones realizadas sobre un tercero.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Historial obtenido correctamente"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_VIEW_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> getHistory(@PathVariable Long id) {
                return changeHistoryService.getHistory(id);
        }

        @GetMapping("/{id}/role-assignments")
        @Operation(summary = "Asignaciones de roles con vigencia", description = "TER-04: Retorna las asignaciones de roles del tercero con fechas de vigencia.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Asignaciones obtenidas correctamente"),
                        @ApiResponse(responseCode = "403", description = "Sin permisos")
        })
        @PreAuthorize("hasAuthority('PERM_VIEW_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<?> getRoleAssignments(@PathVariable Long id) {
                List<RoleAssignmentDTO> assignments = roleAssignmentRepository
                                .findByThirdPartyIdAndDeletedAtIsNull(id)
                                .stream()
                                .map(ra -> RoleAssignmentDTO.builder()
                                                .id(ra.getId())
                                                .thirdPartyId(ra.getThirdParty().getId())
                                                .roleId(ra.getRole().getId())
                                                .roleName(ra.getRole().getName())
                                                .validFrom(ra.getValidFrom())
                                                .validTo(ra.getValidTo())
                                                .build())
                                .collect(Collectors.toList());
                return ResponseEntity.ok(
                                com.sigcon.backend.utils.SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Asignaciones de roles obtenidas correctamente."),
                                                Optional.of(assignments)));
        }

        /**
         * TER-09: Exportar maestro de terceros en CSV o XLSX.
         * HU-TER-09 E1/E3 (2026-04-27): acepta filtros opcionales por rol y
         * estado. Ej: GET /export/csv?role=Cliente&status=Activo
         */
        @GetMapping("/export/{format}")
        @PreAuthorize("hasAuthority('PERM_EXPORT_THIRD_PARTY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
        public ResponseEntity<byte[]> export(
                        @PathVariable String format,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) String role,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) String status) {
                String fmt = format.toUpperCase();
                byte[] data;
                String contentType;
                String filename;

                if ("CSV".equals(fmt)) {
                        data = exportService.exportCsv(role, status);
                        contentType = "text/csv";
                        filename = "terceros_export.csv";
                } else if ("XLSX".equals(fmt)) {
                        data = exportService.exportExcel(role, status);
                        contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                        filename = "terceros_export.xlsx";
                } else {
                        return ResponseEntity.badRequest().build();
                }

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=" + filename)
                                .header("Content-Type", contentType)
                                .body(data);
        }
}

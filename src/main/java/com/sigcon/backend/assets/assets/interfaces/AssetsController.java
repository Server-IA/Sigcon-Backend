package com.sigcon.backend.assets.assets.interfaces;

import com.sigcon.backend.assets.assets.application.BulkAssetsUploadRequest;
import com.sigcon.backend.assets.assets.application.CreateAssetsDTO;
import com.sigcon.backend.assets.assets.application.UpdateAssetsDTO;
import com.sigcon.backend.assets.assets.application.ViewAssetsDTO;
import com.sigcon.backend.assets.assets.domain.service.AssetsService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
@Tag(name = "4. Módulo de Activos - Activos", description = "Endpoints para gestionar activos (CRU)")
public class AssetsController {

        private final AssetsService assetsService;

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<?> handleJsonParseError(HttpMessageNotReadableException ex) {
                Throwable rootCause = ex.getMostSpecificCause();
                return ResponseEntity.badRequest().body(
                                ErrorRespondJson.getErrorRespondMessage(Optional.of(rootCause.getMessage())));
        }

        @PostMapping("/store")
        @PreAuthorize("hasAuthority('PERM_CREATE_ASSET') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Registrar activo", description = "ACT-RF-01: crea un activo con validaciones contables y de terceros.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Activo registrado correctamente", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> store(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Datos requeridos para crear el activo.") @Valid @RequestBody CreateAssetsDTO request,
                        @Parameter(hidden = true) BindingResult bindingResult) {
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }
                ViewAssetsDTO asset = assetsService.create(request);

                return ResponseEntity.status(HttpStatus.CREATED).body(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Activo registrado correctamente"),
                                                Optional.of(asset)));
        }

        @PostMapping("/bulk/store")
        @PreAuthorize("hasAuthority('PERM_CREATE_ASSET') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Carga masiva de activos", description = "Importa activos desde archivo CSV/XLSX enviado en base64. "
                        +
                        "Valida columnas, integridad y reglas contables antes de guardar.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Carga masiva procesada correctamente", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "400", description = "Archivo invalido o error en alguna fila", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "403", description = "Sin permisos", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> bulkStore(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Nombre del archivo, contenido base64 y delimitador (si es CSV).") @Valid @RequestBody BulkAssetsUploadRequest request,
                        @Parameter(hidden = true) BindingResult bindingResult) {
                return assetsService.bulkStore(request, bindingResult);
        }

        @PostMapping("/search")
        @PreAuthorize("hasAuthority('PERM_VIEW_ASSET') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Consultar activos", description = "Consulta activos en formato DataTable.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "400", description = "Solicitud invalida", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
                return ResponseEntity.ok(assetsService.findAllPaged(request));
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_VIEW_ASSET') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Detalle de activo", description = "Obtiene la informacion de un activo especifico.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "400", description = "Activo no encontrado", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> detail(@PathVariable Long id) {
                ViewAssetsDTO asset = assetsService.getById(id);
                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Detalle del activo obtenido correctamente."),
                                                Optional.of(asset)));
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_UPDATE_ASSET') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Editar activo", description = "ACT-RF-09: actualiza un activo existente.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Activo actualizado correctamente", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> update(
                        @Parameter(description = "ID del activo a actualizar", example = "1") @PathVariable Long id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Datos requeridos para actualizar el activo.") @Valid @RequestBody UpdateAssetsDTO request,
                        @Parameter(hidden = true) BindingResult bindingResult) {
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                ViewAssetsDTO updated = assetsService.update(id, request);
                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Activo actualizado correctamente."),
                                                Optional.of(updated)));
        }
}
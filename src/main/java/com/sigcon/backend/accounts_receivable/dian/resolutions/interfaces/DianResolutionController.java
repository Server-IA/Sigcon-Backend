package com.sigcon.backend.accounts_receivable.dian.resolutions.interfaces;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.accounts_receivable.dian.resolutions.application.DianResolutionRequest;
import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.service.DianResolutionService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para la gestion de resoluciones DIAN de numeracion
 * de facturacion electronica (AR-17).
 */
@RestController
@RequestMapping("/api/v1/ar/dian/resolutions")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Facturacion Electronica DIAN",
     description = "Endpoints para resoluciones DIAN, generacion de XML UBL 2.1, CUFE, envio al PSE y representacion grafica en PDF")
public class DianResolutionController {

    private final DianResolutionService service;

    @Operation(summary = "Listar resoluciones DIAN",
               description = "Listado paginado de resoluciones DIAN con filtros DataTable")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente")
    })
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_READ_DIAN_RESOLUTION') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        return service.search(request);
    }

    @Operation(summary = "Obtener resolucion DIAN por ID",
               description = "Retorna la informacion de una resolucion DIAN registrada")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resolucion encontrada"),
        @ApiResponse(responseCode = "400", description = "Resolucion no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_READ_DIAN_RESOLUTION') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return service.getById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Crear resolucion DIAN",
               description = "Registra una nueva resolucion DIAN autorizada con rango, vigencia y clave tecnica")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resolucion creada"),
        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_DIAN_RESOLUTION') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> store(@Valid @RequestBody DianResolutionRequest request,
                                   BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return service.store(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Actualizar resolucion DIAN",
               description = "Actualiza los datos de una resolucion DIAN existente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resolucion actualizada"),
        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_DIAN_RESOLUTION') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody DianResolutionRequest request,
                                    BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return service.update(id, request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Eliminar resolucion DIAN",
               description = "Elimina logicamente una resolucion DIAN")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resolucion eliminada"),
        @ApiResponse(responseCode = "400", description = "Resolucion no encontrada")
    })
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_DIAN_RESOLUTION') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return service.delete(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Alertas de resoluciones DIAN",
               description = "Retorna resoluciones con menos del 5% de rango disponible o menos de 30 dias para expirar")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alertas retornadas")
    })
    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('PERM_READ_DIAN_RESOLUTION') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> alerts() {
        return service.checkAlerts();
    }
}

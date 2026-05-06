package com.sigcon.backend.assets.disposals.interfaces.controller;

import com.sigcon.backend.assets.disposals.application.CreateDisposalRequest;
import com.sigcon.backend.assets.disposals.domain.service.AssetDisposalService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Controlador REST para bajas y transferencias de activos fijos.
 * ACT-03: Endpoints para listar, crear y consultar disposiciones.
 */
@RestController
@RequestMapping("/api/v1/assets/disposals")
@RequiredArgsConstructor
@Tag(name = "8. Modulo de Activos - Bajas y Transferencias",
     description = "Endpoints para gestionar bajas y transferencias de activos fijos")
public class AssetDisposalController {

    private final AssetDisposalService assetDisposalService;

    /**
     * Maneja errores de parseo JSON en el body del request.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleJsonParseError(HttpMessageNotReadableException ex) {
        Throwable rootCause = ex.getMostSpecificCause();
        return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(rootCause.getMessage())));
    }

    /**
     * Listado paginado de disposiciones con filtros dinamicos.
     *
     * @param request parametros de paginacion y filtros DataTable
     * @return respuesta paginada con disposiciones
     */
    @PostMapping("")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Listar disposiciones de activos",
               description = "ACT-03: Obtiene listado paginado de bajas y transferencias con filtros dinamicos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<?> getDisposals(@RequestBody DataTableRequest request) {
        return ResponseEntity.ok(assetDisposalService.getDisposals(request));
    }

    /**
     * Registra una nueva baja o transferencia de activo.
     *
     * @param request datos de la disposicion
     * @param bindingResult resultado de validacion
     * @return disposicion creada o errores de validacion
     */
    @PostMapping("/store")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Registrar baja o transferencia de activo",
               description = "ACT-03: Crea una disposicion, actualiza estado del activo y genera asiento contable.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Disposicion registrada correctamente",
                         content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio",
                         content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<?> createDisposal(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos requeridos para registrar la baja o transferencia.")
            @Valid @RequestBody CreateDisposalRequest request,
            @Parameter(hidden = true) BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        // QA-2026-05-05: el service retorna ErrorRespondJson cuando hay error de
        // negocio (periodo cerrado, fecha anterior a adq, saldos pendientes, etc.).
        // Antes el controller envolvia TODO en HTTP 201, dejando que el frontend
        // viera "exito" aunque el body dijera code:400. Ahora detectamos el tipo
        // y devolvemos HTTP 400 cuando aplica.
        Object result = assetDisposalService.createDisposal(request);
        if (result instanceof ErrorRespondJson) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Obtiene el detalle de una disposicion por su identificador.
     *
     * @param id identificador de la disposicion
     * @return detalle de la disposicion o error
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener disposicion por ID",
               description = "ACT-03: Consulta el detalle de una baja o transferencia especifica.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Disposicion obtenida correctamente"),
            @ApiResponse(responseCode = "400", description = "Disposicion no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    public ResponseEntity<?> getDisposal(@PathVariable Long id) {
        return ResponseEntity.ok(assetDisposalService.getDisposal(id));
    }
}

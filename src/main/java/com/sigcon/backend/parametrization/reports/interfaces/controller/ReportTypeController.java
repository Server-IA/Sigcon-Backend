package com.sigcon.backend.parametrization.reports.interfaces.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.parametrization.reports.application.ReportTypeRequest;
import com.sigcon.backend.parametrization.reports.domain.service.ReportTypeService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para la gestion de tipos de reporte.
 * Proporciona endpoints CRUD con control de acceso basado en roles.
 */
@RestController
@RequestMapping("/api/report-types")
@RequiredArgsConstructor
@Tag(name = "1. Módulo de Parametrización - Tipos de Reporte y Plantillas",
        description = "Endpoints para gestión de tipos de reporte")
public class ReportTypeController {

    private final ReportTypeService reportTypeService;

    /**
     * Obtiene la lista paginada de tipos de reporte.
     *
     * @param request parametros de paginacion y filtros DataTable
     * @return lista paginada de tipos de reporte
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_REPORT_TYPES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Listar tipos de reporte", description = "Obtiene la lista paginada de tipos de reporte con filtros")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de tipos de reporte obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de paginacion invalidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public ResponseEntity<?> getReportTypes(
            @RequestBody(required = false) DataTableRequest request) {
        return reportTypeService.getReportTypes(request);
    }

    /**
     * Crea un nuevo tipo de reporte.
     *
     * @param request datos del tipo de reporte
     * @param bindingResult resultado de la validacion
     * @return tipo de reporte creado o errores de validacion
     */
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_REPORT_TYPES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Crear tipo de reporte", description = "Registra un nuevo tipo de reporte en el sistema")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tipo de reporte creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos o error de validacion"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "409", description = "Ya existe un tipo de reporte con el mismo nombre")
    })
    public ResponseEntity<?> storeReportType(
            @Valid @RequestBody ReportTypeRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(
                    com.sigcon.backend.utils.ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        return reportTypeService.storeReportType(request);
    }

    /**
     * Actualiza un tipo de reporte existente.
     *
     * @param id      identificador del tipo de reporte
     * @param request datos actualizados
     * @param bindingResult resultado de la validacion
     * @return confirmacion de actualizacion o errores
     */
    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_REPORT_TYPES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Actualizar tipo de reporte", description = "Actualiza un tipo de reporte existente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tipo de reporte actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos o error de validacion"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Tipo de reporte no encontrado")
    })
    public ResponseEntity<?> updateReportType(
            @PathVariable Long id,
            @Valid @RequestBody ReportTypeRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(
                    com.sigcon.backend.utils.ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        return reportTypeService.updateReportType(id, request);
    }

    /**
     * Elimina (soft delete) un tipo de reporte.
     *
     * @param id identificador del tipo de reporte
     * @return confirmacion de eliminacion o error de dependencia
     */
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_REPORT_TYPES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Eliminar tipo de reporte", description = "Elimina un tipo de reporte si no tiene plantillas activas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tipo de reporte eliminado exitosamente"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Tipo de reporte no encontrado"),
        @ApiResponse(responseCode = "409", description = "No se puede eliminar, tiene plantillas activas asociadas")
    })
    public ResponseEntity<?> deleteReportType(@PathVariable Long id) {
        return reportTypeService.deleteReportType(id);
    }
}

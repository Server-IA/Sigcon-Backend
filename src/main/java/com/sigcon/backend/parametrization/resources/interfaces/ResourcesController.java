package com.sigcon.backend.parametrization.resources.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;

// NOTA: Todos los @RequestBody en este controller usan org.springframework.web.bind.annotation.RequestBody
// NO usar io.swagger.v3.oas.annotations.parameters.RequestBody para deserialización

import com.sigcon.backend.parametrization.resources.application.AssignWithholdingRequest;
import com.sigcon.backend.parametrization.resources.domain.service.ResourceService;
import com.sigcon.backend.parametrization.resources.domain.service.SystemWithholdingAssignmentService;
import com.sigcon.backend.utils.DataTableRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
// Se usa @org.springframework.web.bind.annotation.RequestBody en los métodos directamente
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@Tag(name = "1. Módulo de Parametrización - Recursos", description = "Endpoints para gestion de recursos")

public class ResourcesController {

    private final ResourceService resourceService;
    private final SystemWithholdingAssignmentService systemWithholdingAssignmentService;

    @PostMapping("/countries")
    @PreAuthorize("hasAuthority('PERM_VIEW_COUNTRY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener países", description = "Obtener países del sistema <br> Permiso requerido: VIEW_COUNTRY")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de paises obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver paises")
    })
    public ResponseEntity<?> getCountries(@org.springframework.web.bind.annotation.RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getCountries(dtRequest);
    }

    @PostMapping("/municipalities")
    @PreAuthorize("hasAuthority('PERM_VIEW_MUNICIPALITY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener municipios", description = "Obtener municipios del sistema <br> Permiso requerido: VIEW_MUNICIPALITY")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de municipios obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver municipios")
    })
    public ResponseEntity<?> getMunicipalities(@org.springframework.web.bind.annotation.RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getMunicipalities(dtRequest);
    }

    // ===== CRUD PAÍSES =====
    @PostMapping("/countries/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_COUNTRY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Crear un nuevo pais", description = "Registra un pais en el catalogo de recursos <br> Permiso requerido: CREATE_COUNTRY")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pais creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del pais invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para crear paises"),
        @ApiResponse(responseCode = "409", description = "Ya existe un pais con ese nombre o codigo")
    })
    public ResponseEntity<?> createCountry(@org.springframework.web.bind.annotation.RequestBody CountryDTO request) { return resourceService.createCountry(request); }

    @PutMapping("/countries/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_COUNTRY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Actualizar un pais existente", description = "Actualiza los datos de un pais del catalogo <br> Permiso requerido: UPDATE_COUNTRY")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pais actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del pais invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para actualizar paises"),
        @ApiResponse(responseCode = "404", description = "Pais no encontrado")
    })
    public ResponseEntity<?> updateCountry(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestBody CountryDTO request) { return resourceService.updateCountry(id, request); }

    @DeleteMapping("/countries/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_COUNTRY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Eliminar un pais (soft delete)", description = "Elimina logicamente un pais del catalogo <br> Permiso requerido: DELETE_COUNTRY")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pais eliminado exitosamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para eliminar paises"),
        @ApiResponse(responseCode = "404", description = "Pais no encontrado")
    })
    public ResponseEntity<?> deleteCountry(@PathVariable Long id) { return resourceService.deleteCountry(id); }

    // ===== CRUD MUNICIPIOS =====
    @PostMapping("/municipalities/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_MUNICIPALITY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Crear un nuevo municipio", description = "Registra un municipio en el catalogo de recursos <br> Permiso requerido: CREATE_MUNICIPALITY")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Municipio creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del municipio invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para crear municipios"),
        @ApiResponse(responseCode = "409", description = "Ya existe un municipio con ese nombre o codigo")
    })
    public ResponseEntity<?> createMunicipality(@org.springframework.web.bind.annotation.RequestBody MunicipalityDTO request) { return resourceService.createMunicipality(request); }

    @PutMapping("/municipalities/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_MUNICIPALITY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Actualizar un municipio existente", description = "Actualiza los datos de un municipio del catalogo <br> Permiso requerido: UPDATE_MUNICIPALITY")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Municipio actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del municipio invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para actualizar municipios"),
        @ApiResponse(responseCode = "404", description = "Municipio no encontrado")
    })
    public ResponseEntity<?> updateMunicipality(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestBody MunicipalityDTO request) { return resourceService.updateMunicipality(id, request); }

    @DeleteMapping("/municipalities/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_MUNICIPALITY') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Eliminar un municipio (soft delete)", description = "Elimina logicamente un municipio del catalogo <br> Permiso requerido: DELETE_MUNICIPALITY")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Municipio eliminado exitosamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para eliminar municipios"),
        @ApiResponse(responseCode = "404", description = "Municipio no encontrado")
    })
    public ResponseEntity<?> deleteMunicipality(@PathVariable Long id) { return resourceService.deleteMunicipality(id); }

    @PostMapping("/payment-terms")
    @PreAuthorize("hasAuthority('PERM_VIEW_PAYMENT_TERM') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener términos de pago", description = "Obtener términos de pago del sistema <br> Permiso requerido: VIEW_PAYMENT_TERM")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de terminos de pago obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver terminos de pago")
    })
    public ResponseEntity<?> getPaymentTerms(@org.springframework.web.bind.annotation.RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getAllPaymentTerms(dtRequest);
    }

    @PostMapping("/types-regimes")
    @PreAuthorize("hasAuthority('PERM_VIEW_TYPES_REGIMES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener tipos de regímenes", description = "Obtener tipos de regímenes del sistema <br> Permiso requerido: VIEW_TYPES_REGIMES")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de tipos de regimenes obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver tipos de regimenes")
    })
    public ResponseEntity<?> getTypesRegimes(@org.springframework.web.bind.annotation.RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getAllTypesRegimes(dtRequest);
    }

    @PostMapping("/types-organizations")
    @PreAuthorize("hasAuthority('PERM_VIEW_TYPES_ORGANIZATIONS') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener tipos de organizaciones", description = "Obtener tipos de organizaciones del sistema <br> Permiso requerido: VIEW_TYPES_ORGANIZATIONS")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de tipos de organizaciones obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver tipos de organizaciones")
    })
    public ResponseEntity<?> getTypesOrganizations(@org.springframework.web.bind.annotation.RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getAllTypesOrganizations(dtRequest);
    }

    @PostMapping("/withholdings")
    @PreAuthorize("hasAuthority('PERM_VIEW_WITHHOLDINGS') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener retenciones", description = "Obtener retenciones del sistema <br> Permiso requerido: VIEW_WITHHOLDINGS")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de retenciones obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver retenciones")
    })
    public ResponseEntity<?> getWithholdings(@org.springframework.web.bind.annotation.RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getAllWithholdings(dtRequest);
    }

    @PostMapping("/payment-forms")
    @PreAuthorize("hasAuthority('PERM_VIEW_PAYMENT_FORM') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener formas de pago", description = "Obtener formas de pago del sistema <br> Permiso requerido: VIEW_PAYMENT_FORM")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de formas de pago obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver formas de pago")
    })
    public ResponseEntity<?> getPaymentForms(@org.springframework.web.bind.annotation.RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getAllPaymentForms(dtRequest);
    }

    // ===== ASIGNACIONES DE RETENCIONES DEL SISTEMA =====

    @PostMapping("/system-withholdings")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Listar asignaciones de retenciones", description = "Obtiene la lista paginada de retenciones asignadas al sistema <br> Permiso requerido: ROLE_ADMIN")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de asignaciones de retenciones obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador")
    })
    public ResponseEntity<?> getSystemWithholdings(@org.springframework.web.bind.annotation.RequestBody(required = false) DataTableRequest dtRequest) {
        return systemWithholdingAssignmentService.getAssignments(dtRequest);
    }

    @PostMapping("/system-withholdings/assign")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Asignar retencion al sistema", description = "Asigna una retencion existente al sistema con fecha de vigencia <br> Permiso requerido: ROLE_ADMIN")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Retencion asignada exitosamente al sistema"),
        @ApiResponse(responseCode = "400", description = "Datos de asignacion invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador"),
        @ApiResponse(responseCode = "409", description = "La retencion ya esta asignada al sistema")
    })
    public ResponseEntity<?> assignSystemWithholding(@Valid @org.springframework.web.bind.annotation.RequestBody AssignWithholdingRequest request) {
        return systemWithholdingAssignmentService.assignWithholding(request);
    }

    @DeleteMapping("/system-withholdings/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Desasignar retencion del sistema", description = "Elimina (soft delete) una asignacion de retencion del sistema <br> Permiso requerido: ROLE_ADMIN")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Asignacion de retencion eliminada exitosamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos de administrador"),
        @ApiResponse(responseCode = "404", description = "Asignacion de retencion no encontrada")
    })
    public ResponseEntity<?> unassignSystemWithholding(@PathVariable Long id) {
        return systemWithholdingAssignmentService.unassignWithholding(id);
    }

}

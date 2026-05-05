package com.sigcon.backend.third_parties.commercial_data.interfaces;

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

import com.sigcon.backend.third_parties.commercial_data.application.CommercialDataRequest;
import com.sigcon.backend.third_parties.commercial_data.domain.service.CommercialDataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/commercial-data")
@RequiredArgsConstructor
@Tag(name = "3. Módulo de Terceros - Datos comerciales", description = "Endpoints para la gestión de datos comerciales")
public class CommercialDataController {
    private final CommercialDataService commercialDataService;

    /**
     * POST /api/v1/commercial-data
     */
    @Operation(summary = "Crear datos comerciales de un tercero", description = "Crea un nuevo registro de datos comerciales para un tercero. "
            +
            "Si ya existe un registro vigente para el tercero, retorna HTTP 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Datos comerciales creados exitosamente"),
            @ApiResponse(responseCode = "400", description = "CD_001: El tercero no existe"),
            @ApiResponse(responseCode = "409", description = "CD_002: Ya existen datos comerciales vigentes para este tercero")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CREATE_COMMERCIAL_DATA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> create(
            @Valid @RequestBody CommercialDataRequest request,
            BindingResult bindingResult) {
        return commercialDataService.create(request, bindingResult);
    }

    /**
     * PUT /api/v1/commercial-data/{thirdPartyId}
     */
    @Operation(summary = "Actualizar datos comerciales de un tercero", description = "Actualiza el registro de datos comerciales vigente de un tercero. "
            +
            "Si no existe un registro vigente, retorna HTTP 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos comerciales actualizados exitosamente"),
            @ApiResponse(responseCode = "400", description = "CD_001: El tercero no existe"),
            @ApiResponse(responseCode = "404", description = "CD_003: No existen datos comerciales vigentes para este tercero")
    })
    @PutMapping("/{thirdPartyId}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_COMMERCIAL_DATA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable Long thirdPartyId,
            @Valid @RequestBody CommercialDataRequest request,
            BindingResult bindingResult) {
        return commercialDataService.update(thirdPartyId, request, bindingResult);
    }

    /**
     * GET /api/v1/commercial-data/{thirdPartyId}
     */
    @Operation(summary = "Consultar datos comerciales de un tercero", description = "Retorna el registro de datos comerciales vigente de un tercero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos comerciales retornados exitosamente"),
            @ApiResponse(responseCode = "404", description = "CD_003: No existen datos comerciales vigentes para este tercero")
    })
    @GetMapping("/{thirdPartyId}")
    @PreAuthorize("hasAuthority('PERM_VIEW_COMMERCIAL_DATA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getByThirdParty(@PathVariable Long thirdPartyId) {
        return commercialDataService.getByThirdParty(thirdPartyId);
    }

    /**
     * DELETE /api/v1/commercial-data/{thirdPartyId}
     */
    @Operation(summary = "Eliminar datos comerciales de un tercero", description = "Realiza un soft delete del registro de datos comerciales vigente de un tercero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos comerciales eliminados exitosamente"),
            @ApiResponse(responseCode = "404", description = "CD_003: No existen datos comerciales vigentes para este tercero")
    })
    @DeleteMapping("/{thirdPartyId}")
    @PreAuthorize("hasAuthority('PERM_DELETE_COMMERCIAL_DATA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long thirdPartyId) {
        return commercialDataService.delete(thirdPartyId);
    }

    /**
     * GET /api/v1/commercial-data/{thirdPartyId}/history
     */
    @Operation(summary = "Consultar historial de cambios de datos comerciales",
            description = "Retorna el historial de cambios realizados sobre los datos comerciales de un tercero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de cambios obtenido exitosamente"),
            @ApiResponse(responseCode = "404", description = "CD_003: No existen datos comerciales vigentes para este tercero")
    })
    @GetMapping("/{thirdPartyId}/history")
    @PreAuthorize("hasAuthority('PERM_VIEW_COMMERCIAL_DATA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getHistory(@PathVariable Long thirdPartyId) {
        return commercialDataService.getHistory(thirdPartyId);
    }
}

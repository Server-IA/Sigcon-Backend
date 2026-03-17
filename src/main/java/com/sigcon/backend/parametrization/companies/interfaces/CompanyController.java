package com.sigcon.backend.parametrization.companies.interfaces;

import com.sigcon.backend.parametrization.companies.application.CreateCompanyLocationRequest;
import com.sigcon.backend.parametrization.companies.application.CreateCompanyRequest;
import com.sigcon.backend.parametrization.companies.application.UpdateCompanyLocationRequest;
import com.sigcon.backend.parametrization.companies.application.UpdateCompanyRequest;
import com.sigcon.backend.parametrization.companies.domain.service.CompanyService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Companies", description = "Endpoints para gestion de compañías y sus sedes")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyService companyService;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBusinessException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage()))
        );
    }

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Registrar compañía",
            description = "Crea una nueva compañía con sus datos generales y su sede principal. " +
                    "Los campos obligatorios son: name, nit, dv, typeRegimeId, typeOrganizationId y locations. " +
                    "El name debe ser unico. La combinacion nit + dv debe ser unica. " +
                    "Debe proporcionar al menos una sede (la primera sera la sede principal). " +
                    "Requiere el permiso PERM_CREATE_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compania registrada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio (name duplicado, NIT+DV duplicado, campos faltantes)",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> store(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos de la compañía a crear. Los campos name, nit, dv, typeRegimeId, typeOrganizationId y locations son obligatorios.",
                    content = @Content(schema = @Schema(implementation = CreateCompanyRequest.class))
            )
            @Valid @RequestBody CreateCompanyRequest request,
            BindingResult bindingResult
    ) {
        return companyService.create(request, bindingResult);
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Consultar compañías",
            description = "Consulta paginada de compañías usando el payload de DataTables. " +
                    "Retorna las compañías con sus sedes, ubicaciones y retenciones asignadas. " +
                    "Requiere el permiso PERM_VIEW_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Parametros invalidos o sin resultados",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> search(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = false,
            description = "Objeto DataTableRequest con filtros, orden y paginacion."
            )
            @RequestBody(required = false) DataTableRequest request
    ) {
        return companyService.findAllPaged(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VIEW_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Detalle de compañía",
            description = "Retorna el detalle completo de la compañía incluyendo sus sedes, ubicaciones y retenciones asignadas. " +
                    "Requiere el permiso PERM_VIEW_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Compania no encontrada",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> detail(
            @Parameter(description = "ID unico de la compañía", example = "1")
            @PathVariable Long id) {
        return companyService.getDetail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Actualizar compañía",
            description = "Actualiza la informacion de una compañía existente. Todos los campos son opcionales, " +
                    "solo se actualizaran los campos proporcionados. Si se actualiza el name, debe seguir siendo unico. " +
                    "Si se actualiza nit o dv, la combinacion debe seguir siendo unica. " +
                    "Requiere el permiso PERM_UPDATE_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compania actualizada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio (name duplicado, NIT+DV duplicado)",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> update(
            @Parameter(description = "ID unico de la compañía a actualizar", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos a actualizar de la compañía.",
                    content = @Content(schema = @Schema(implementation = UpdateCompanyRequest.class))
            )
            @Valid @RequestBody UpdateCompanyRequest request,
            BindingResult bindingResult
    ) {
        return companyService.update(id, request, bindingResult);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Eliminar compañía",
            description = "Realiza la eliminacion logica de la compañía, sus sedes asociadas y sus retenciones asignadas. " +
                    "Requiere el permiso PERM_DELETE_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compania eliminada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Compania no encontrada o ya eliminada",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> delete(
            @Parameter(description = "ID unico de la compañía a eliminar", example = "1")
            @PathVariable Long id) {
        return companyService.delete(id);
    }

    @DeleteMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Eliminar sede de compañía",
            description = "Realiza la eliminacion logica de una sede de compañía. " +
                    "No se puede eliminar la sede principal si es la unica sede activa. " +
                    "Requiere el permiso PERM_DELETE_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sede eliminada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Sede no encontrada, ya eliminada o es la unica sede activa",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> deleteLocation(
            @Parameter(description = "ID de la sede a eliminar", example = "1")
            @PathVariable Long id) {
        return companyService.deleteLocation(id);
    }

    @PostMapping("/{companyId}/locations/store")
    @PreAuthorize("hasAuthority('PERM_UPDATE_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Crear sede de compañía",
            description = "Crea una nueva sede (sucursal) para la compañía indicada. Las sedes adicionales siempre serán sucursales (is_main = false). " +
                    "Solo puede haber una sede principal por compañía. Los campos name, address y municipalityId son obligatorios. " +
                    "Requiere el permiso PERM_UPDATE_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sede creada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> createLocation(
            @Parameter(description = "ID de la compañía", example = "1")
            @PathVariable Long companyId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos de la sede a crear. Los campos name, address y municipalityId son obligatorios.",
                    content = @Content(schema = @Schema(implementation = CreateCompanyLocationRequest.class))
            )
            @Valid @RequestBody CreateCompanyLocationRequest request,
            BindingResult bindingResult
    ) {
        return companyService.createLocation(companyId, request, bindingResult);
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Actualizar sede de compañía",
            description = "Actualiza los datos de una sede existente. Todos los campos son opcionales. " +
                    "Si se marca isMain como true, esta sede pasara a ser la principal y las demas se marcaran como sucursales. " +
                    "Requiere el permiso PERM_UPDATE_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sede actualizada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> updateLocation(
            @Parameter(description = "ID de la sede a actualizar", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos a actualizar de la sede.",
                    content = @Content(schema = @Schema(implementation = UpdateCompanyLocationRequest.class))
            )
            @Valid @RequestBody UpdateCompanyLocationRequest request,
            BindingResult bindingResult
    ) {
        return companyService.updateLocation(id, request, bindingResult);
    }

    @PostMapping("/{companyId}/withholdings/{withholdingId}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Asignar retencion a compañía",
            description = "Asigna una retencion (ReteFuente, ReteIVA, ReteICA, etc.) a una compañía. " +
                    "La retencion debe ser unica por compañía (no se puede asignar la misma retencion dos veces). " +
                    "Requiere el permiso PERM_UPDATE_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retencion asignada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error de validacion o retencion ya asignada",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> assignWithholding(
            @Parameter(description = "ID de la compañía", example = "1")
            @PathVariable Long companyId,
            @Parameter(description = "ID de la retencion", example = "1")
            @PathVariable Long withholdingId
    ) {
        return companyService.assignWithholding(companyId, withholdingId);
    }

    @DeleteMapping("/{companyId}/withholdings/{withholdingId}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_COMPANY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(
            summary = "Eliminar retencion de compañía",
            description = "Elimina la asignacion de una retencion a una compañía mediante eliminacion logica. " +
                    "Requiere el permiso PERM_UPDATE_COMPANY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retencion eliminada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error de validacion o retencion no asignada",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> removeWithholding(
            @Parameter(description = "ID de la compañía", example = "1")
            @PathVariable Long companyId,
            @Parameter(description = "ID de la retencion", example = "1")
            @PathVariable Long withholdingId
    ) {
        return companyService.removeWithholding(companyId, withholdingId);
    }
}

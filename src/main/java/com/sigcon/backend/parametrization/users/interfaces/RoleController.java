package com.sigcon.backend.parametrization.users.interfaces;

import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;
import com.sigcon.backend.parametrization.users.application.role.RoleRequest;
import com.sigcon.backend.parametrization.users.application.role.UpdateUserRole;
import com.sigcon.backend.parametrization.users.domain.service.RoleService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "1. Módulo de Parametrización - Roles y permisos del sistema", description = "Endpoints para gestion de roles y permisos del sistema")
public class RoleController {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleJsonParseError(HttpMessageNotReadableException ex) {

        Throwable rootCause = ex.getMostSpecificCause();

        return ResponseEntity
                .badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(rootCause.getMessage())));
    }

    private final RoleService roleService;

    @PostMapping("/getRoles")
    @PreAuthorize("hasAuthority('PERM_VIEW_ROLES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Listar roles con filtros y paginacion", description = "Obtiene la lista paginada de roles del sistema <br> Permiso requerido: VIEW_ROLES")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de roles obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver roles")
    })
    public ResponseEntity<?> getRoles(@RequestBody(required = false) DataTableRequest request) {
        return roleService.getRoles(request);
    }

    @PostMapping("/createRole")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Crear un nuevo rol", description = "Crea un rol en el sistema con nombre y descripcion <br> Permiso requerido: CREATE_ROLE")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rol creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del rol invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para crear roles"),
        @ApiResponse(responseCode = "409", description = "Ya existe un rol con ese nombre")
    })
    public ResponseEntity<?> createRole(@RequestBody RoleRequest request) {
        return roleService.createRole(request);
    }

    @PutMapping("/updateRole/{id}")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Actualizar un rol existente", description = "Actualiza nombre y/o descripcion de un rol <br> Permiso requerido: UPDATE_ROLE")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rol actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del rol invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para actualizar roles"),
        @ApiResponse(responseCode = "404", description = "Rol no encontrado")
    })
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        return roleService.updateRole(id, request);
    }

    @PostMapping("/deleteRole/{id}")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Eliminar rol (soft delete)", description = "Elimina logicamente un rol del sistema <br> Permiso requerido: DELETE_ROLE")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rol eliminado exitosamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para eliminar roles"),
        @ApiResponse(responseCode = "404", description = "Rol no encontrado")
    })
    public ResponseEntity<?> deleteRole(@PathVariable Long id) {
        return roleService.deleteRole(id);
    }

    @PostMapping("/assignRole")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_ROLE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Asignar rol a usuario", description = "Asigna un rol existente a un usuario del sistema <br> Permiso requerido: ASSIGN_ROLE")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rol asignado exitosamente al usuario"),
        @ApiResponse(responseCode = "400", description = "Datos de asignacion invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para asignar roles"),
        @ApiResponse(responseCode = "404", description = "Rol o usuario no encontrado")
    })
    public ResponseEntity<?> assignRoleToUser(@RequestBody UpdateUserRole request) {
        return roleService.assignRoleToUser(request);
    }

    @PostMapping("/createPermission")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Crear un nuevo permiso", description = "Crea un permiso en el sistema con codigo, nombre y descripcion <br> Permiso requerido: CREATE_PERMISSION")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Permiso creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del permiso invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para crear permisos"),
        @ApiResponse(responseCode = "409", description = "Ya existe un permiso con ese codigo")
    })
    public ResponseEntity<?> createPermission(@Valid @RequestBody PermissionDTO request, BindingResult bindingResult) {
        return roleService.createPermission(request, bindingResult);
    }

    @PutMapping("/updatePermission/{id}")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Actualizar permiso existente", description = "Actualiza codigo, nombre y/o descripcion de un permiso <br> Permiso requerido: UPDATE_PERMISSION")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Permiso actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del permiso invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para actualizar permisos"),
        @ApiResponse(responseCode = "404", description = "Permiso no encontrado")
    })
    public ResponseEntity<?> updatePermission(@PathVariable Long id, @Valid @RequestBody PermissionDTO request,
            BindingResult bindingResult) {
        return roleService.updatePermission(id, request, bindingResult);
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('PERM_VIEW_PERMISSIONS') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Listar permisos con filtros", description = "Obtiene la lista paginada de permisos del sistema <br> Permiso requerido: VIEW_PERMISSIONS")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista de permisos obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parametros de consulta invalidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para ver permisos")
    })
    public ResponseEntity<?> getPermissions(@RequestBody DataTableRequest dtRequest) {
        return roleService.getPermissions(dtRequest);
    }

    @PostMapping("/assign-permissions")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Asignar permisos a un rol", description = "Asigna una lista de permisos a un rol existente <br> Permiso requerido: ASSIGN_PERMISSION")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Permisos asignados exitosamente al rol"),
        @ApiResponse(responseCode = "400", description = "Datos de asignacion invalidos o rol/permisos no encontrados"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para asignar permisos")
    })
    public ResponseEntity<?> assignPermissions(@RequestBody RoleRequest request) {

        try {
            roleService.assignPermissions(request);
            return ResponseEntity.ok(
                    Map.of("success", true, "message", "Permisos asignados correctamente al rol"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage()));

        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("success", false, "message", "Error al asignar permisos al rol"));
        }
    }

    @PostMapping("/remove-permissions")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Remover permisos de un rol", description = "Remueve una lista de permisos de un rol existente <br> Permiso requerido: REMOVE_PERMISSION")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Permisos removidos exitosamente del rol"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos o rol/permisos no encontrados"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para remover permisos")
    })
    public ResponseEntity<?> removePermissions(@RequestBody RoleRequest request) {
        return roleService.removePermissions(request);
    }

    @DeleteMapping("/deletePermission/{id}")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Eliminar permiso (soft delete)", description = "Elimina logicamente un permiso del sistema <br> Permiso requerido: DELETE_PERMISSION")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Permiso eliminado exitosamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos para eliminar permisos"),
        @ApiResponse(responseCode = "404", description = "Permiso no encontrado")
    })
    public ResponseEntity<?> deletePermission(@PathVariable Long id) {
        return roleService.deletePermission(id);
    }

}

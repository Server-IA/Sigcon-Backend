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

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
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
    @PreAuthorize("hasAuthority('PERM_VIEW_ROLES')")
    public ResponseEntity<?> getRoles(@RequestBody(required = false) DataTableRequest request) {
        return roleService.getRoles(request);
    }


    @PostMapping("/createRole")
    @PreAuthorize("hasAuthority('PERM_CREATE_ROLE')")
    public ResponseEntity<?> createRole(@RequestBody RoleRequest request) {
        return roleService.createRole(request);
    }

    @PutMapping("/updateRole/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_ROLE')")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        return roleService.updateRole(id, request);
    }

    @PostMapping("/deleteRole/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_ROLE')")
    public ResponseEntity<?> deleteRole(@PathVariable Long id) {
        return roleService.deleteRole(id);
    }

    @PostMapping("/assignRole")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_ROLE')")
    public ResponseEntity<?> assignRoleToUser(@RequestBody UpdateUserRole request) {
        return roleService.assignRoleToUser(request);
    }

    @PostMapping("/createPermission")
    @PreAuthorize("hasAuthority('PERM_CREATE_PERMISSION')")
    public ResponseEntity<?> createPermission(@Valid @RequestBody PermissionDTO request, BindingResult bindingResult) {
        return roleService.createPermission(request, bindingResult);
    }

    @PutMapping("/updatePermission/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_PERMISSION')")
    public ResponseEntity<?> updatePermission(@PathVariable Long id, @Valid @RequestBody PermissionDTO request, BindingResult bindingResult) {
        return roleService.updatePermission(id, request, bindingResult);
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('PERM_VIEW_PERMISSIONS')")
    public ResponseEntity<?> getPermissions(@RequestBody DataTableRequest dtRequest) {
        return roleService.getPermissions(dtRequest);
    }

    @PostMapping("/assign-permissions")
    public ResponseEntity<?> assignPermissions(@RequestBody RoleRequest request) {

        try {
            roleService.assignPermissions(request);
            return ResponseEntity.ok(
                    Map.of("success", true, "message", "Permisos asignados correctamente al rol")
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("success", false, "message", "Error al asignar permisos al rol")
            );
        }
    }

    @PostMapping("/remove-permissions")
    @PreAuthorize("hasAuthority('PERM_REMOVE_PERMISSION')")
    public ResponseEntity<?> removePermissions(@RequestBody RoleRequest request) {
        return roleService.removePermissions(request);
    }

}

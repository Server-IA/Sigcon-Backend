package com.sigcon.backend.users.interfaces;

import com.sigcon.backend.users.application.RoleRequest;
import com.sigcon.backend.users.domain.model.Role;
import com.sigcon.backend.users.domain.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_ROLES')")
    public ResponseEntity<Page<Role>> getRoles(@RequestParam(required = false) String name, Pageable pageable) {
        Page<Role> roles = roleService.getRoles(name, pageable);

        if (roles.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(roles);
    }

    @PostMapping("/createRole")
    @PreAuthorize("hasAuthority('PERM_CREATE_ROLE')")
    public ResponseEntity<?> createRole(@RequestBody RoleRequest request) {
        return roleService.createRole(request);
    }



}

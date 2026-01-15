package com.sigcon.backend.users.domain.service;

import com.sigcon.backend.users.application.RoleRequest;
import com.sigcon.backend.users.domain.model.Permission;
import com.sigcon.backend.users.domain.model.Role;
import com.sigcon.backend.users.domain.repository.PermissionRepository;
import com.sigcon.backend.users.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    private final PermissionRepository permissionRepository;

    public Page<Role> getRoles(String name, Pageable pageable) {
        if (name == null || name.isBlank()) {
            return roleRepository.findAll(pageable);
        }
        return roleRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    public ResponseEntity<?> createRole(RoleRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre del rol es obligatorio");
        }

        if (roleRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("El rol ya existe.");
        }

        Set<Permission> permissions = Set.of();

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = permissionRepository.findAllById(request.getPermissionIds())
                    .stream().collect(Collectors.toSet());
        }

        Role role = Role.builder()
                .name(request.getName().toUpperCase())
                .permissions(permissions)
                .build();

        roleRepository.save(role);

        return ResponseEntity.ok("Rol creado exitosamente");
    }

}

package com.sigcon.backend.users.domain.service;

import com.sigcon.backend.users.application.role.RoleRequest;
import com.sigcon.backend.users.application.role.UpdateUserRole;
import com.sigcon.backend.users.domain.model.Permission;
import com.sigcon.backend.users.domain.model.Role;
import com.sigcon.backend.users.domain.model.User;
import com.sigcon.backend.users.domain.model.enums.Status;
import com.sigcon.backend.users.domain.repository.PermissionRepository;
import com.sigcon.backend.users.domain.repository.RoleRepository;
import com.sigcon.backend.users.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public Page<Role> getRoles(String name, Pageable pageable) { //solo muestra los activos (o sea los no eliminados)

        if (name == null || name.isBlank()) {
            return roleRepository.findByStatus(Status.ACTIVE, pageable);
        }

        return roleRepository.findByNameContainingIgnoreCaseAndStatus(
                name,
                Status.ACTIVE,
                pageable
        );
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
                .status(Status.ACTIVE)
                .build();

        roleRepository.save(role);

        return ResponseEntity.ok("Rol creado exitosamente");
    }

    public ResponseEntity<?> updateRole(Long id, RoleRequest request){
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre del rol es obligatorio");
        }

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        if (roleRepository.findByName(request.getName()).isPresent() && !role.getName().equalsIgnoreCase(request.getName())) {
            return ResponseEntity.badRequest().body("El rol ya existe.");
        }

        Set<Permission> permissions = Set.of();

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = permissionRepository.findAllById(request.getPermissionIds())
                    .stream().collect(Collectors.toSet());
        }

        role.setName(request.getName().toUpperCase());
        role.setPermissions(permissions);
        role.setStatus(Status.valueOf(request.getStatus()));


        roleRepository.save(role);

        return ResponseEntity.ok("Rol actualizado exitosamente");
    }

    public ResponseEntity<?> deleteRole(Long id) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        if (role.getStatus() == Status.INACTIVE) {
            return ResponseEntity.badRequest().body("El rol ya se encuentra inactivo");
        }

        boolean hasUsers = !userRepository.findAllByRoles_Name(role.getName()).isEmpty();

        if (hasUsers) {
            return ResponseEntity.badRequest().body("No se puede eliminar el rol porque está asociado a usuarios");
        }

        role.setStatus(Status.INACTIVE);
        roleRepository.save(role);

        return ResponseEntity.ok("Rol eliminado exitosamente");
    }

    public ResponseEntity<?> assignRoleToUser(UpdateUserRole request){

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        user.getRoles().clear();
        user.getRoles().add(role);
        user.setLastUpdateDate(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok("Rol asignado correctamente al usuario.");
    }



}

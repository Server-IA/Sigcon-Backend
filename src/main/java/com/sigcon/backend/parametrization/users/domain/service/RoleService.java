package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;
import com.sigcon.backend.parametrization.users.application.role.RoleRequest;
import com.sigcon.backend.parametrization.users.application.role.UpdateUserRole;
import com.sigcon.backend.parametrization.users.domain.model.Permission;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.model.enums.TypePermits;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final MenuRepositoryPort menuRepository;

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
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "El nombre del rol es obligatorio")
            );
        }

        if (roleRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "El rol ya existe.")
            );
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

        return ResponseEntity.ok(
                Map.of("success", true, "message", "Rol creado exitosamente")
        );
    }

    public ResponseEntity<?> updateRole(Long id, RoleRequest request){

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "El nombre del rol es obligatorio")
            );
        }

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        if (roleRepository.findByName(request.getName()).isPresent()
                && !role.getName().equalsIgnoreCase(request.getName())) {

            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "El rol ya existe.")
            );
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

        return ResponseEntity.ok(
                Map.of("success", true, "message", "Rol actualizado exitosamente")
        );
    }

    public ResponseEntity<?> deleteRole(Long id) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        if (role.getStatus() == Status.INACTIVE) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "El rol ya se encuentra inactivo")
            );
        }

        boolean hasUsers = !userRepository.findAllByRoles_Name(role.getName()).isEmpty();

        if (hasUsers) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "No se puede eliminar el rol porque está asociado a usuarios")
            );
        }

        role.setStatus(Status.INACTIVE);
        roleRepository.save(role);

        return ResponseEntity.ok(
                Map.of("success", true, "message", "Rol eliminado exitosamente")
        );
    }

    public ResponseEntity<?> assignRoleToUser(UpdateUserRole request){

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        user.getRoles().clear();
        user.getRoles().add(role);
        user.setUpdated_at(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of("success", true, "message", "Rol asignado correctamente al usuario.")
        );
    }

    public ResponseEntity<?> createPermission(PermissionDTO request){

        if (request == null || request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "El nombre del permiso es obligatorio")
            );
        }

        if (permissionRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "El permiso ya existe.")
            );
        }

        MenuEntity menu = request.getMenu_id() == null
                ? null
                : menuRepository.findById(request.getMenu_id());

        Permission permission = permissionRepository.findByName(request.getName())
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder()
                        .name(request.getName())
                        .type(request.getType())
                        .description(request.getDescription())
                        .menu(menu)
                        .build()
                ));

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            Set<Role> roles = roleRepository.findAllById(request.getRoleIds())
                    .stream().collect(Collectors.toSet());

            for (Role role : roles) {
                role.getPermissions().add(permission);
                roleRepository.save(role);
            }
        }

        permissionRepository.save(permission);

        return ResponseEntity.ok(
                Map.of("success", true, "message", "Permiso creado exitosamente")
        );
    }

    public ResponseEntity<?> getPermissions(Pageable pageable) {

        try {
            Page<Permission> permissions = permissionRepository.findAll(pageable);

            if (permissions.isEmpty()) {
                return ResponseEntity.ok(
                        Map.of("success", true, "message", "No se encontraron permisos")
                );
            }

            return ResponseEntity.ok(permissions);

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error al cargar permisos"));
        }
    }

    @Transactional
    public ResponseEntity<?> assignPermissions(RoleRequest request) {

        Role role = roleRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));

        Set<Permission> permissions = new HashSet<>(
                permissionRepository.findAllById(request.getPermissionIds())
        );

        if (permissions.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "No se encontraron permisos válidos")
            );
        }

        role.getPermissions().addAll(permissions);
        roleRepository.save(role);

        return ResponseEntity.ok(
                Map.of("success", true, "message", "Permisos asignados correctamente al rol")
        );
    }

    @Transactional
    public ResponseEntity<?> removePermissions(RoleRequest request) {

        try {
            Role role = roleRepository.findById(request.getId())
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

            if (role.getPermissions().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "El rol no tiene permisos asignados")
                );
            }

            role.getPermissions().removeIf(
                    permission -> request.getPermissionIds().contains(permission.getId())
            );

            roleRepository.save(role);

            return ResponseEntity.ok(
                    Map.of("success", true, "message", "Permisos removidos correctamente del rol")
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error al remover permisos del rol"));
        }
    }



}

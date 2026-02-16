package com.sigcon.backend.parametrization.users.domain.service;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleDataTableRequest;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;
import com.sigcon.backend.parametrization.users.application.role.RoleRequest;
import com.sigcon.backend.parametrization.users.application.role.UpdateUserRole;
import com.sigcon.backend.parametrization.users.domain.model.Permission;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.model.enums.TypePermits;
import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;

import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
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
    private final ModuleRepository moduleRepository;

    private final DataTableSpecificationBuilder<Role> roleSpecificationBuilder =
        new DataTableSpecificationBuilder<>();

    private final DataTableSpecificationBuilder<Permission> permissionSpecificationBuilder =
        new DataTableSpecificationBuilder<>();

    public ResponseEntity<?> getRoles(DataTableRequest request) {

        try {
            int start  = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<Role> spec = roleSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deleted_at")));

            Page<Role> roles = roleRepository.findAll(spec, pageable);

            Page<RoleRequest> data = roles.map(role -> {
                RoleRequest dto = new RoleRequest();
                dto.setId(role.getId());
                dto.setName(role.getName());
                dto.setPermissionIds(
                    role.getPermissions().stream().map(Permission::getId).collect(Collectors.toSet())
                );
                dto.setPermissions(
                    role.getPermissions().stream().map(permission -> PermissionDTO.builder()
                        .name(permission.getName())
                        .type(permission.getType())
                        .description(permission.getDescription())
                        .build()).collect(Collectors.toList()));
                dto.setStatus(role.getStatus().name());
                return dto;
            });

            return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al obtener los roles")));
        }

        // return roleRepository.findAllAndDeletedAtIsNull(request.getPageable());
    }

    public ResponseEntity<?> createRole(RoleRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del rol es obligatorio"))
            );
        }

        if (roleRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("El rol ya existe."))
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
            SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol creado exitosamente"), Optional.of(role))
        );
    }

    public ResponseEntity<?> updateRole(Long id, RoleRequest request){

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del rol es obligatorio"))
            );
        }

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        if (roleRepository.findByName(request.getName()).isPresent()
                && !role.getName().equalsIgnoreCase(request.getName())) {

            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("El rol ya existe."))
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
            SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol actualizado exitosamente"), Optional.of(role))
        );
    }

    public ResponseEntity<?> deleteRole(Long id) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        if (role.getDeleted_at() != null) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("El rol ya se encuentra eliminado"))
            );
        }

        boolean hasUsers = !userRepository.findAllByRoles_Name(role.getName()).isEmpty();

        if (hasUsers) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("No se puede eliminar el rol porque está asociado a usuarios"))
            );
        }

        role.setDeleted_at(LocalDateTime.now());
        roleRepository.save(role);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol eliminado exitosamente"), Optional.of(role))
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

    public ResponseEntity<?> createPermission(PermissionDTO request, BindingResult bindingResult){

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        try{

            Optional<Permission> optionalPermission = permissionRepository.findByCode(request.getCode());

            if (optionalPermission.isPresent()) {
                throw new RuntimeException("El código del permiso ya existe");
            }
            
            ModuleEntity module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new RuntimeException("El módulo no existe"));

            Permission permission = permissionRepository.save(
                Permission.builder()
                    .name(request.getName())
                    .code(request.getCode())
                    .type(request.getType())
                    .description(request.getDescription())
                    .module(module)
                    .build()
            );
    
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
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permiso creado exitosamente"), Optional.of(permission))
            );

        }catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    public ResponseEntity<?> getPermissions(DataTableRequest request) {

        try {

            int start  = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<Permission> spec = permissionSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deleted_at")));

            Page<Permission> permissions = permissionRepository.findAll(spec, pageable);

            Page<PermissionDTO> data = permissions.map(this::toDTO);

            return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al obtener los permisos")));
        }
    }

    public ResponseEntity<?> updatePermission(Long id, PermissionDTO request, BindingResult bindingResult){
        try{
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            Optional<Permission> optionalPermission = permissionRepository.findById(id);

            if (!optionalPermission.isPresent()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Permiso no encontrado")));
            }

            Permission permission = optionalPermission.get();
            Optional<Permission> searchCode = permissionRepository.findByCodeNotId(request.getCode(), permission.getId());

            if (searchCode.isPresent()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of("El código del permiso ya existe")));
            }

            ModuleEntity module = moduleRepository.findById(request.getModuleId()).orElseThrow(() -> new RuntimeException("El módulo no existe"));

            permission.setName(request.getName());
            permission.setCode(request.getCode());
            permission.setType(request.getType());
            permission.setDescription(request.getDescription());
            permission.setModule(module);
            permission.setUpdated_at(LocalDateTime.now());
            permissionRepository.save(permission);

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permiso actualizado exitosamente"), Optional.of(permission))
            );

        }catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
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
                ErrorRespondJson.getErrorRespondMessage(Optional.of("No se encontraron permisos válidos"))
            );
        }

        role.getPermissions().addAll(permissions);
        roleRepository.save(role);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permisos asignados correctamente al rol"), Optional.of(role))
        );
    }

    @Transactional
    public ResponseEntity<?> removePermissions(RoleRequest request) {

        try {
            Role role = roleRepository.findById(request.getId())
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

            if (role.getPermissions().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of("El rol no tiene permisos asignados"))
                );
            }

            role.getPermissions().removeIf(
                    permission -> request.getPermissionIds().contains(permission.getId())
            );

            roleRepository.save(role);

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permisos removidos correctamente del rol"), Optional.of(role))
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al remover permisos del rol"))
            );
        }
    }

    private PermissionDTO toDTO(Permission permission) {
        return PermissionDTO.builder()
            .id(permission.getId())
            .name(permission.getName())
            .code(permission.getCode())
            .type(permission.getType())
                .description(permission.getDescription())
                .roleIds(
                    roleRepository.findAllByPermissions_Id(permission.getId()).stream().map(Role::getId).collect(Collectors.toSet())
                )
                .module(
                    ModuleDTO.builder()
                        .id(permission.getModule().getId())
                        .name(permission.getModule().getName())
                        .build()
                )
                .build();
    }
}

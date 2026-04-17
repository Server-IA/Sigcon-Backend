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

    /**
     * Lista roles del sistema con paginacion y filtros DataTable.
     * Excluye roles eliminados logicamente (deletedAt != null).
     *
     * @param request parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de roles y sus permisos asociados
     */
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
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

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
                dto.setStatus(parseStatus(role.getStatus()));
                return dto;
            });

            return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }

        // return roleRepository.findAllAndDeletedAtIsNull(request.getPageable());
    }

    /**
     * Crea un nuevo rol en el sistema.
     * Valida que el nombre sea obligatorio y unico (case-insensitive, se almacena en mayusculas).
     * Opcionalmente asocia permisos existentes al rol.
     *
     * @param request datos del rol (nombre, IDs de permisos opcionales)
     * @return ResponseEntity con el rol creado o mensaje de error por duplicidad
     */
    public ResponseEntity<?> createRole(RoleRequest request) {
        try{

            if (request.getName() == null || request.getName().isBlank()) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del rol es obligatorio"))
                );
            }
    
            if (roleRepository.findByNameAndDeletedAtIsNull(request.getName().toUpperCase()).isPresent()) {
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
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
    
            roleRepository.save(role);
    
            RoleRequest roleDTO = toRequest(role);
    
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol creado exitosamente"), Optional.of(roleDTO))
            );

        }catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Actualiza un rol existente (nombre, permisos y estado).
     * Valida unicidad del nombre excluyendo el registro actual para evitar falsos positivos.
     *
     * @param id      ID del rol a actualizar
     * @param request datos actualizados del rol
     * @return ResponseEntity con el rol actualizado o mensaje de error
     */
    public ResponseEntity<?> updateRole(Long id, RoleRequest request){

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del rol es obligatorio"))
            );
        }

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        if (roleRepository.findByNameAndDeletedAtIsNull(request.getName().toUpperCase()).isPresent()
                && !role.getName().equalsIgnoreCase(request.getName())) {

            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("El rol ya existe."))
            );
        }

        Set<Permission> permissions = new HashSet<>();

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = permissionRepository.findAllById(request.getPermissionIds())
                    .stream().collect(Collectors.toSet());
        }

        role.setName(request.getName().toUpperCase());
        role.setPermissions(permissions);
        role.setStatus(Status.valueOf(request.getStatus()));

        roleRepository.save(role);

        RoleRequest roleDTO = toRequest(role);

        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol actualizado exitosamente"), Optional.of(roleDTO))
        );
    }

    /**
     * Elimina un rol de forma logica (soft delete).
     * Valida que no este asignado a ningun usuario antes de eliminar para evitar inconsistencias.
     *
     * @param id ID del rol a eliminar
     * @return ResponseEntity con mensaje de exito o error si tiene usuarios asociados
     */
    public ResponseEntity<?> deleteRole(Long id) {

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        if (role.getDeletedAt() != null) {
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

        role.setDeletedAt(LocalDateTime.now());
        roleRepository.save(role);

        RoleRequest roleDTO = toRequest(role);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol eliminado exitosamente"), Optional.of(roleDTO))
        );
    }

    /**
     * Asigna un rol a un usuario, reemplazando todos los roles previos.
     * El sistema actual maneja un solo rol por usuario (reemplazo completo).
     *
     * @param request contiene userId y roleId para la asignacion
     * @return ResponseEntity con mensaje de exito o error si usuario/rol no existe
     */
    public ResponseEntity<?> assignRoleToUser(UpdateUserRole request){

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        user.getRoles().clear();
        user.getRoles().add(role);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of("success", true, "message", "Rol asignado correctamente al usuario.")
        );
    }

    /**
     * Crea un nuevo permiso y opcionalmente lo asigna a roles existentes.
     * Valida unicidad del codigo del permiso y existencia del modulo asociado.
     *
     * @param request       datos del permiso (nombre, codigo, tipo, modulo, roles opcionales)
     * @param bindingResult resultado de validacion de campos obligatorios
     * @return ResponseEntity con el permiso creado o errores de validacion
     */
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

            PermissionDTO permissionDTO = toDTO(permission);
    
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permiso creado exitosamente"), Optional.of(permissionDTO))
            );

        }catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Lista permisos del sistema con paginacion y filtros DataTable.
     * Excluye permisos eliminados logicamente.
     *
     * @param request parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de permisos
     */
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

    /**
     * Actualiza un permiso existente (nombre, codigo, tipo, descripcion, modulo).
     * Valida unicidad del codigo excluyendo el registro actual.
     *
     * @param id            ID del permiso a actualizar
     * @param request       datos actualizados del permiso
     * @param bindingResult resultado de validacion de campos obligatorios
     * @return ResponseEntity con el permiso actualizado o errores de validacion
     */
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
            Optional<Permission> searchCode = permissionRepository.findByCodeAndIdNot(request.getCode(), permission.getId());

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
            
            PermissionDTO permissionDTO = toDTO(permission);

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permiso actualizado exitosamente"), Optional.of(permissionDTO))
            );

        }catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }

    }

    /**
     * Asigna un conjunto de permisos a un rol existente (operacion aditiva).
     * Los permisos nuevos se agregan sin eliminar los ya asignados.
     *
     * @param request contiene el ID del rol y los IDs de permisos a asignar
     * @return ResponseEntity con mensaje de exito o error si el rol/permisos no existen
     */
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

    /**
     * Remueve permisos especificos de un rol.
     * Solo elimina los permisos indicados, conservando los demas.
     *
     * @param request contiene el ID del rol y los IDs de permisos a remover
     * @return ResponseEntity con mensaje de exito o error si el rol no tiene permisos
     */
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

    /**
     * PA-RF-24: Elimina un permiso del sistema de forma FISICA (hard delete).
     *
     * <p>La HU especifica "borrado fisico con confirmacion": el permiso
     * se elimina definitivamente de la BD, no solo se marca como eliminado.
     *
     * <p>Reglas de negocio:
     * <ul>
     *   <li>El permiso debe existir.</li>
     *   <li>No debe estar asignado a ningun rol antes de eliminarlo (integridad referencial).</li>
     * </ul>
     *
     * @param id ID del permiso a eliminar
     * @return 200 si la eliminacion fue exitosa; 400 si esta asignado a roles o no existe
     */
    public ResponseEntity<?> deletePermission(Long id) {
        try {
            Permission permission = permissionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Permiso no encontrado"));

            // Verificar si esta asignado a algun rol
            long rolesCount = roleRepository.findAllByPermissions_Id(id).size();
            if (rolesCount > 0) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("No se puede eliminar el permiso porque esta asignado a " + rolesCount + " rol(es)"))
                );
            }

            // PA-RF-24: borrado FISICO (no soft delete)
            permissionRepository.delete(permission);

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permiso eliminado correctamente"), Optional.empty())
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
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

    private RoleRequest toRequest(Role role) {
        return RoleRequest.builder()
            .id(role.getId())
            .name(role.getName())
            .permissionIds(role.getPermissions().stream().map(Permission::getId).collect(Collectors.toSet()))
            .permissions(role.getPermissions().stream().map(this::toDTO).collect(Collectors.toList()))
            .status(role.getStatus().name())
            .build();
    }

    private String parseStatus(Status status) {
        return status.name();
    }
}

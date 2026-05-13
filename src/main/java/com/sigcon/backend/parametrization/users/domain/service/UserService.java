package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.general.storage.AvatarStorageService;
import com.sigcon.backend.parametrization.parameters.application.ParameterDTO;
import com.sigcon.backend.parametrization.parameters.application.UserParameterDTO;
import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import com.sigcon.backend.parametrization.parameters.domain.repository.UserParameterRepository;
import com.sigcon.backend.parametrization.parameters.domain.service.SystemInfoService;
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;
import com.sigcon.backend.parametrization.users.application.user.UserDTO;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.platform.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionRepository permissionRepository;
    private final ParameterRepository parameterRepository;
    private final UserParameterRepository userParameterRepository;
    private final RoleRepository roleRepository;
    private final SystemInfoService systemInfoService;
    private final AuditPublisher auditPublisher;

    /**
     * QA Bloque PA Bug 48 (HU-PA-20 E3/E4, 2026-05-09): NotificationService inyectado
     * por setter (evitar ciclo) para emitir USER_DEACTIVATED y USER_ROLE_ADDED.
     */
    private com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotificationService(
            com.sigcon.backend.parametrization.notifications.domain.service.NotificationService ns) {
        this.notificationService = ns;
    }

    private final UserUtil userUtil;

    private final AvatarStorageService avatarStorageService;
    private final DataTableSpecificationBuilder<User> userSpecificationBuilder = new DataTableSpecificationBuilder<>();

    public ResponseEntity<?> getUsers(DataTableRequest request) {

        try {
            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                    ? Pageable.unpaged()
                    : PageRequest.of(page, safeLength);
            Specification<User> spec = userSpecificationBuilder.build(request)
                    .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            // Bloque AM (2026-05-03): User NO tiene @Filter("tenantFilter")
            // intencionalmente para que PLATFORM_ADMIN pueda ver todos los users
            // de todas las empresas. Pero un ADMIN_EMPRESA llamando este endpoint
            // recibia TODOS los users (leak cross-tenant). Filtrar manualmente
            // por TenantContext cuando NO es PLATFORM_ADMIN.
            if (!TenantContext.isPlatformAdmin()) {
                Long currentTenant = TenantContext.getCompanyId();
                if (currentTenant != null) {
                    spec = spec.and((root, query, cb) -> cb.equal(root.get("companyId"), currentTenant));
                } else {
                    // usuario sin tenant ni platform => no ve nada (defensivo)
                    spec = spec.and((root, query, cb) -> cb.isFalse(cb.literal(true)));
                }
            }

            Page<User> users = userRepository.findAll(spec, pageable);

            Page<UserDTO> data = users.map(user -> {
                UserDTO dto = new UserDTO();
                dto.setId(user.getId());
                dto.setName(user.getName());
                dto.setLastname(user.getLastname());
                dto.setEmail(user.getEmail());
                dto.setAvatar(user.getAvatar());
                dto.setStatus(user.getStatus());
                dto.setUsername(user.getUsername());
                dto.setRoles(
                        user.getRoles()
                                .stream()
                                .map(Role::getName)
                                .collect(Collectors.toSet()));
                return dto;
            });

            DataTableResponse<UserDTO> response = DataTableResponse.from(data, request.getDraw());
            // response.setRecordsTotal(data.getTotalElements());
            // response.setRecordsFiltered(data.getTotalElements());

            return ResponseEntity.ok(
                    response);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    private boolean noFilters(UserDTO dto) {
        return isBlank(dto.getName())
                && isBlank(dto.getLastname())
                && isBlank(dto.getEmail())
                && isBlank(dto.getUsername())
                && isBlank(dto.getRole())
                && dto.getStatus() == null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public ResponseEntity<?> store(UserDTO request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            // QA Bloque PA Bug 19 (HU-PA-08 E6, 2026-05-09): mensaje especifico cuando
            // el email ya pertenece a un usuario de plataforma. La HU exige reflejar
            // el constraint ck_users_tenant_or_platform a nivel de aplicacion.
            Optional<User> existingOpt = userRepository.findByEmail(request.getEmail());
            if (existingOpt.isPresent() && existingOpt.get().getPlatformRole() != null) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                "Un usuario no puede ser simultaneamente usuario de plataforma y usuario de empresa")));
            }
            // QA Bloque PA Bug 18 (HU-PA-08 E3): mensaje literal HU para email duplicado cross-tenant.
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Ya existe un usuario con ese email en el sistema")));
        }

        // QA Bloque PA Bug 17 (HU-PA-08 E4, 2026-05-09): validar al menos un rol
        // asignado al crear usuario. La HU exige bloquear con mensaje literal.
        if (request.getRoles() == null || request.getRoles().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe asignar al menos un rol al usuario")));
        }

        // Multi-tenant: el usuario nuevo debe pertenecer a la empresa del admin que lo crea.
        // El constraint ck_users_tenant_or_platform exige company_id NOT NULL XOR platform_role NOT NULL.
        // Desde /parametrizacion/users solo se crean usuarios tenant; PLATFORM_ADMIN usa su propio flujo.
        Long tenantCompanyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (tenantCompanyId == null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "No se puede crear el usuario: el administrador actual no pertenece a ninguna empresa. "
                          + "Los usuarios de plataforma se crean desde el modulo Plataforma.")));
        }

        // QA Bloque PA Bug 16 (HU-PA-08 E2): resolver cada rol POR TENANT.
        // findByName devolvia el primer rol global cuando los roles eran globales,
        // ahora cada empresa tiene sus copias y necesitamos filtrar por companyId.
        Set<Role> resolvedRoles = new java.util.HashSet<>();
        java.util.List<String> notFound = new java.util.ArrayList<>();
        for (String roleName : request.getRoles()) {
            if (roleName == null || roleName.isBlank()) continue;
            Optional<Role> opt = roleRepository.findByNameIgnoreCaseAndCompanyIdAndDeletedAtIsNull(
                    roleName.trim().toUpperCase(), tenantCompanyId);
            if (opt.isPresent()) {
                resolvedRoles.add(opt.get());
            } else {
                notFound.add(roleName);
            }
        }
        if (resolvedRoles.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Ninguno de los roles solicitados existe en esta empresa: " + notFound)));
        }

        User user = User.builder()
                .name(request.getName())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .status(Status.ACTIVE)
                .companyId(tenantCompanyId)
                .roles(resolvedRoles)
                .build();
        userRepository.save(user);
        // HU-PA-08 audit: incluir roles asignados en la descripcion
        String rolesStr = resolvedRoles.stream().map(Role::getName).sorted().collect(Collectors.joining(", "));
        auditPublisher.publishCreate(AuditModule.PA, "User", user.getId(),
                "Usuario creado: " + user.getEmail() + " | roles=[" + rolesStr + "] | companyId=" + tenantCompanyId);
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Usuario creado correctamente"),
                        Optional.empty()));
    }

    public ResponseEntity<?> getUserInfo() {

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            UserDTO response = new UserDTO();
            response.setId(user.getId());
            response.setName(user.getName());
            response.setLastname(user.getLastname());
            response.setEmail(user.getEmail());
            response.setAvatar(user.getAvatar());
            response.setStatus(user.getStatus());

            // Datos de empresa se leen desde parametros del sistema (mono-empresa)
            Map<String, String> companyInfo = systemInfoService.getSystemInfo();

            response.setRoles(
                    user.getRoles()
                            .stream()
                            .map(Role::getName)
                            .collect(Collectors.toSet()));

            response.setPermissions(
                    permissionRepository.findByUserID(user.getId())
                            .stream()
                            .map(permission -> new PermissionDTO(
                                    null,
                                    permission.getName(),
                                    permission.getCode(),
                                    permission.getType(),
                                    null,
                                    null,
                                    permission.getDescription(),
                                    null))
                            .collect(Collectors.toList()));

            List<ParameterDTO> parameters = parameterRepository.findAll()
                    .stream()
                    .map(parameter -> new ParameterDTO(
                            null,
                            parameter.getName(),
                            parameter.getValue(),
                            userParameterRepository.findByUserAndParameter(user, parameter)
                                    .map(userParameter -> new UserParameterDTO(
                                            null,
                                            null,
                                            null,
                                            userParameter.getValue(),
                                            null,
                                            null,
                                            null,
                                            null))
                                    .orElse(null),
                            parameter.getCategory(),
                            parameter.getStatus(),
                            null,
                            null,
                            null))
                    .collect(Collectors.toList());

            response.setParameters(parameters);

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Información del usuario obtenida correctamente"), Optional.of(response)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }

    }

    public ResponseEntity<?> updateInfo(UserDTO request) {

        if (request == null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Datos inválidos")));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        if (request.getLastname() != null) {
            user.setLastname(request.getLastname());
        }

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }

        if (request.getAvatar() != null && !request.getAvatar().isBlank()) {
            user.setAvatar(resolveAvatarFilename(request.getAvatar(), user.getAvatar()));
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        auditPublisher.publishUpdate(AuditModule.PA, "User", user.getId(), "User actualizado id=" + user.getId());

        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setName(user.getName());
        userDTO.setLastname(user.getLastname());
        userDTO.setEmail(user.getEmail());
        userDTO.setAvatar(user.getAvatar());
        userDTO.setStatus(user.getStatus());
        userDTO.setRoles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
        userDTO.setPermissions(permissionRepository.findByUserID(user.getId()).stream()
                .map(permission -> new PermissionDTO(null, permission.getName(), permission.getCode(),
                        permission.getType(), null, null, permission.getDescription(), null))
                .collect(Collectors.toList()));

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Información actualizada correctamente"),
                        Optional.of(userDTO)));
    }

    public ResponseEntity<?> updateUser(Long id, UserDTO request) {

        // try{

        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Usuario no encontrado")));
        }

        User user = userOpt.get();

        // QA Bloque PA Bug 48 (HU-PA-20 E3/E4, 2026-05-09): capturar estado previo
        // para detectar cambios que requieren notificar al user (status + roles).
        Status previousStatus = user.getStatus();
        Set<String> previousRoles = user.getRoles() == null ? new java.util.HashSet<>()
                : user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(java.util.HashSet::new));

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        if (request.getLastname() != null) {
            user.setLastname(request.getLastname());
        }

        // QA Bloque PA Bug 22 (HU-PA-09 E4, 2026-05-09): validar unicidad global del
        // email al editar. Antes el endpoint hacia setEmail sin validar y permitia
        // duplicados que rompian el login con "Query did not return a unique result".
        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailAndIdNotAndDeletedAtIsNull(request.getEmail(), id)) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                "Ya existe un usuario con ese email en el sistema")));
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        // QA Bloque PA Bugs 20+21 (HU-PA-09 E1/E2/E3, 2026-05-09):
        // - Multi-rol: usar resolucion per-tenant en lugar de findByName cross-tenant.
        // - E3: si el cliente envia un array vacio de roles, bloquear con mensaje literal HU.
        // - El cliente debe enviar roles como List<String> (multi-select). Solo se procesa
        //   si la propiedad viene presente en el request; si llega null se preserva el set actual.
        if (request.getRoles() != null) {
            if (request.getRoles().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                "El usuario debe tener al menos un rol activo. Asigne otro rol antes de remover este")));
            }
            // QA Bloque PA Bug 98 (HU-PA-12 E4, 2026-05-13): el lookup de rol por
            // nombre debe usar la empresa del USUARIO editado, no la del actor.
            // Cuando un PLATFORM_ADMIN edita un usuario tenant, su propio
            // tenantCompanyId es null y el fallback findByName(legacy) elegia un
            // rol global cualquiera (de otra empresa), corrompiendo users_roles
            // con asignaciones cross-tenant. Hoy hay usuarios con N copias de
            // ADMIN_EMPRESA acumuladas por este bug.
            Long targetCompanyId = user.getCompanyId(); // empresa del user editado
            Set<Role> resolvedRoles = new java.util.HashSet<>();
            java.util.List<String> notFound = new java.util.ArrayList<>();
            for (String roleName : request.getRoles()) {
                if (roleName == null || roleName.isBlank()) continue;
                Optional<Role> opt;
                if (targetCompanyId != null) {
                    // user de empresa: rol debe pertenecer a SU empresa
                    opt = roleRepository.findByNameIgnoreCaseAndCompanyIdAndDeletedAtIsNull(
                            roleName.trim().toUpperCase(), targetCompanyId);
                } else {
                    // user de plataforma (companyId=null): roles globales (ADMIN/USER/PLATFORM_ADMIN)
                    opt = roleRepository.findByName(roleName.trim().toUpperCase());
                }
                if (opt.isPresent()) {
                    resolvedRoles.add(opt.get());
                } else {
                    notFound.add(roleName);
                }
            }
            if (resolvedRoles.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                "Ninguno de los roles solicitados existe en esta empresa: " + notFound)));
            }
            user.setRoles(resolvedRoles);
        }

        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }

        user.setUpdatedAt(LocalDateTime.now());

        // QA Bloque PA Bug 79 (HU-PA-11 E4, 2026-05-11): si los roles o el status
        // cambiaron, invalidar sesiones activas del usuario. JwtService.validateToken
        // rechazara cualquier token emitido ANTES de este timestamp y forzara
        // re-login para que los permisos actuales tengan efecto. Antes el usuario
        // seguia operando con permisos viejos hasta que su token expirara.
        Set<String> rolesAfterSet = user.getRoles() == null ? new java.util.HashSet<>()
                : user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        boolean rolesChanged = !previousRoles.equals(rolesAfterSet);
        boolean statusChanged = previousStatus != user.getStatus();
        if (rolesChanged || statusChanged) {
            user.setSessionInvalidatedAt(LocalDateTime.now());
        }

        userRepository.save(user);
        // HU-PA-09 audit: incluir cambios clave (email + roles) para trazabilidad
        String rolesAfter = user.getRoles().stream().map(Role::getName).sorted().collect(Collectors.joining(", "));
        auditPublisher.publishUpdate(AuditModule.PA, "User", user.getId(),
                "User actualizado id=" + user.getId() + " | email=" + user.getEmail()
                + " | roles=[" + rolesAfter + "]"
                + (rolesChanged || statusChanged ? " | session_invalidated=true" : ""));

        // QA Bloque PA Bug 48 (HU-PA-20 E3): notif al desactivar
        if (notificationService != null && previousStatus == Status.ACTIVE && user.getStatus() == Status.INACTIVE) {
            try {
                notificationService.publishToUser(user.getId(),
                    com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                        .companyId(user.getCompanyId())
                        .eventKey("USER_DEACTIVATED")
                        .title("Su cuenta sera desactivada")
                        .body("Su cuenta " + user.getEmail() + " fue desactivada por un administrador. "
                                + "Su sesion sera invalidada en breve. Si requiere reactivarla, contacte al administrador.")
                        .actionUrl("/perfil")
                        .sourceId(user.getId())
                        .sourceType("User")
                        .severity(com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.WARNING)
                        .build());
            } catch (RuntimeException ignored) { /* notif no rompe update */ }
        }
        // HU-PA-20 E4: notif al asignar nuevos roles
        if (notificationService != null) {
            try {
                Set<String> newRoles = user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(java.util.HashSet::new));
                Set<String> added = new java.util.HashSet<>(newRoles); added.removeAll(previousRoles);
                Set<String> removed = new java.util.HashSet<>(previousRoles); removed.removeAll(newRoles);
                if (!added.isEmpty()) {
                    notificationService.publishToUser(user.getId(),
                        com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                            .companyId(user.getCompanyId())
                            .eventKey("USER_ROLE_ADDED")
                            .title("Se le agrego un nuevo rol")
                            .body("Se le agregaron los siguientes roles: " + added
                                    + (removed.isEmpty() ? "" : " (removidos: " + removed + ")"))
                            .actionUrl("/perfil")
                            .sourceId(user.getId())
                            .sourceType("User")
                            .build());
                } else if (!removed.isEmpty()) {
                    notificationService.publishToUser(user.getId(),
                        com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                            .companyId(user.getCompanyId())
                            .eventKey("USER_ROLE_REMOVED")
                            .title("Se le retiro un rol")
                            .body("Se le retiraron los siguientes roles: " + removed)
                            .actionUrl("/perfil")
                            .sourceId(user.getId())
                            .sourceType("User")
                            .build());
                }
            } catch (RuntimeException ignored) { /* notif no rompe update */ }
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Información del usuario actualizada correctamente"), Optional.empty()));
        // }catch(Exception e){
        // return
        // ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    /**
     * PA-RF-13: Eliminar un usuario del sistema via soft delete.
     *
     * <p>Reglas de negocio:
     * <ul>
     *   <li>No se puede eliminar un usuario que no existe o ya fue eliminado.</li>
     *   <li>No se puede eliminar el usuario 'superadmin' (proteccion del usuario seed).</li>
     *   <li>No se puede eliminar un usuario que tenga el rol ADMIN (roles criticos).</li>
     * </ul>
     *
     * @param id ID del usuario a eliminar
     * @return 200 si la eliminacion fue exitosa; 400 si hay violacion de reglas; 404 si no existe
     */
    public ResponseEntity<?> deleteUser(Long id) {
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Usuario no encontrado")));
        } else if (userOpt.get().getDeletedAt() != null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Usuario ya eliminado")));
        }

        User user = userOpt.get();

        // PA-RF-13: proteger usuario 'superadmin' del sistema (seed inicial, no eliminable)
        if ("superadmin".equalsIgnoreCase(user.getUsername())) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("No se puede eliminar el usuario superadmin del sistema")));
        }

        // PA-RF-13: proteger usuarios con rol ADMIN (roles criticos)
        boolean hasAdminRole = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName())
                        || "SUPERADMIN".equalsIgnoreCase(r.getName()));
        if (hasAdminRole) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("No se puede eliminar un usuario con rol ADMIN")));
        }

        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
        auditPublisher.publishDelete(AuditModule.PA, "User", user.getId(), "User eliminado id=" + user.getId());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Usuario eliminado correctamente"),
                        Optional.empty()));
    }

    private String resolveAvatarFilename(String avatarValue, String previousAvatarFilename) {
        if (looksLikeBase64Image(avatarValue)) {
            return avatarStorageService.saveBase64Avatar(avatarValue, previousAvatarFilename);
        }
        return avatarValue;
    }

    private boolean looksLikeBase64Image(String avatarValue) {
        String normalized = avatarValue.trim().toLowerCase();
        return normalized.startsWith("data:image/") || normalized.length() > 255;
    }
}

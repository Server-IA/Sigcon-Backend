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
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

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
    private final AuditPublisher auditPublisher;

    /**
     * QA Bloque PA Bug 75 (HU-PA-03 E3, 2026-05-11): para enriquecer cada Role
     * con su companyName legible en el listado cross-empresa (PLATFORM_ADMIN).
     * Inyeccion opcional para no crear ciclo si algun deploy minimo no carga
     * el modulo platform.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.platform.companies.domain.repository.CompanyRepository companyRepository;
    /**
     * QA Bloque PA Bug 48 (HU-PA-20 E2/E4, 2026-05-09): notificacion personal a
     * los usuarios afectados cuando se editan los permisos del rol o cuando se
     * les agrega un rol. Inyectado por setter para evitar ciclo (notifications
     * tambien depende de roles).
     */
    private com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotificationService(
            com.sigcon.backend.parametrization.notifications.domain.service.NotificationService ns) {
        this.notificationService = ns;
    }

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

            // HU-PA-03 E3 / Bug 2 (2026-05-09): aislamiento estricto multi-tenant.
            // Si el usuario es PLATFORM_ADMIN, ve TODOS los roles (globales + tenant)
            // para administracion. Si es ADMIN_EMPRESA, ve SOLO los roles de su tenant
            // (los globales PLATFORM_ADMIN/ADMIN/USER NO se le muestran).
            final Long tenantId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
            final boolean isPlatform = com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin();

            Specification<Role> spec = roleSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            if (!isPlatform) {
                // Filtrar SOLO roles del tenant actual
                if (tenantId == null) {
                    return ResponseEntity.ok(DataTableResponse.from(Page.empty(pageable), request.getDraw()));
                }
                spec = spec.and((root, query, cb) -> cb.equal(root.get("companyId"), tenantId));
            }

            // Filtro adicional: ?type=PREDEFINED|CUSTOM|GLOBAL (HU-PA-03 E2 / Bug 3)
            String typeFilter = extractTypeFilter(request);
            if (typeFilter != null) {
                final String t = typeFilter.toUpperCase();
                spec = spec.and((root, query, cb) -> {
                    var nameUpper = cb.upper(root.get("name"));
                    var inPredef = nameUpper.in(Role.PREDEFINED_NAMES);
                    var inGlobal = nameUpper.in(Role.SYSTEM_GLOBAL_NAMES);
                    if ("PREDEFINED".equals(t)) return inPredef;
                    if ("CUSTOM".equals(t)) return cb.and(cb.not(inPredef), cb.not(inGlobal));
                    if ("GLOBAL".equals(t)) return inGlobal;
                    return cb.conjunction();
                });
            }

            Page<Role> roles = roleRepository.findAll(spec, pageable);
            Page<RoleRequest> data = roles.map(this::toRequest);

            return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
        } catch (Exception e) {
            // HU-PA-03 E6: error tecnico al cargar listado -> mensaje generico
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("No fue posible cargar los roles. Intente de nuevo o contacte al administrador.")));
        }
    }

    /**
     * Extrae el filtro `type` desde DataTableRequest.columns[].search.value
     * cuando hay una columna llamada "type" o "tipo". Devuelve uno de
     * PREDEFINED / CUSTOM / GLOBAL o null si no se filtro.
     */
    private String extractTypeFilter(DataTableRequest req) {
        if (req == null || req.getColumns() == null) return null;
        for (var col : req.getColumns()) {
            if (col == null || col.getName() == null) continue;
            String n = col.getName().toLowerCase();
            if (("type".equals(n) || "tipo".equals(n))
                && col.getSearch() != null
                && col.getSearch().getValue() != null
                && !col.getSearch().getValue().isBlank()) {
                return col.getSearch().getValue().trim();
            }
        }
        return null;
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
        try {
            if (request.getName() == null || request.getName().isBlank()) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre del rol es obligatorio"))
                );
            }

            // HU-PA-04 E5: validar al menos un permiso seleccionado.
            // (HU dice "Mostrar mensaje 'Debe asignar al menos un permiso al rol'")
            if (request.getPermissionIds() == null || request.getPermissionIds().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe asignar al menos un permiso al rol"))
                );
            }

            // HU-PA-04 E3 / Bug 4 (2026-05-09): unicidad por TENANT, no global.
            // PLATFORM_ADMIN crea roles globales (companyId=NULL). ADMIN_EMPRESA
            // crea roles del tenant actual.
            final Long tenantId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
            final boolean isPlatform = com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin();
            final Long targetCompanyId = isPlatform ? null : tenantId;

            if (!isPlatform && tenantId == null) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                        "El usuario actual no pertenece a una empresa; no puede crear roles."))
                );
            }

            // HU-PA-04 E3: validar nombre unico solo dentro del scope (companyId)
            String upper = request.getName().toUpperCase().trim();
            if (roleRepository.findByNameIgnoreCaseAndCompanyIdAndDeletedAtIsNull(upper, targetCompanyId).isPresent()) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Ya existe un rol con ese nombre en " + (targetCompanyId == null ? "el sistema" : "esta empresa") + "."))
                );
            }

            // HU-PA-04 E3: ADMIN_EMPRESA NO puede usar nombres reservados de roles globales
            if (!isPlatform && Role.SYSTEM_GLOBAL_NAMES.contains(upper)) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Ese nombre esta reservado para roles del sistema."))
                );
            }

            Set<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds())
                    .stream().collect(Collectors.toSet());

            Role role = Role.builder()
                    .name(upper)
                    .description(request.getDescription())
                    .companyId(targetCompanyId)
                    .permissions(permissions)
                    .status(Status.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            roleRepository.save(role);
            auditPublisher.publishCreate(AuditModule.PA, "Role", role.getId(),
                    "Role creado: " + role.getName() + " (companyId=" + role.getCompanyId() + ")");

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol creado exitosamente"), Optional.of(toRequest(role)))
            );

        } catch (Exception e) {
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

        // QA Bloque BC (2026-05-17): bloqueo cross-tenant. ADMIN_EMPRESA solo
        // puede modificar roles de su empresa (PLATFORM_ADMIN bypass).
        assertRoleAccessible(role);

        // HU-PA-05 E4 (QA Bloque PA Bug 9, 2026-05-09): optimistic locking.
        // Si el cliente envia `version` y NO coincide con la actual, el rol
        // fue modificado por otro usuario en el intervalo. Lanza 409 con
        // mensaje literal HU. Si no envia version (clientes legacy), permite
        // continuar pero @Version sigue protegiendo a nivel JPA.
        if (request.getVersion() != null
                && role.getVersion() != null
                && !request.getVersion().equals(role.getVersion())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(
                    "Este rol fue modificado por otro usuario. Recarga los datos y vuelve a intentarlo."))
            );
        }

        // HU-PA-05 E2 (QA Bloque PA, 2026-05-09): si es predefinido, el NOMBRE no se
        // puede cambiar (queda en solo lectura). Solo descripcion y permisos.
        boolean isPredefined = role.isPredefined();
        String upperRequested = request.getName().toUpperCase().trim();

        if (isPredefined && !role.getName().equalsIgnoreCase(upperRequested)) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(
                        "El nombre de los roles predefinidos no puede modificarse."))
            );
        }

        // HU-PA-04 E3 / Bug 4: unicidad scoped al tenant (companyId del rol)
        if (!role.getName().equalsIgnoreCase(upperRequested)
                && roleRepository.findByNameIgnoreCaseAndCompanyIdAndDeletedAtIsNull(upperRequested, role.getCompanyId()).isPresent()) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(
                        "Ya existe un rol con ese nombre en " + (role.getCompanyId() == null ? "el sistema" : "esta empresa") + "."))
            );
        }

        // QA Bloque PA Bug 48 (HU-PA-20 E2): capturar permisos previos para construir
        // el diff (agregados/removidos) y notificar a los usuarios afectados.
        Set<String> previousCodes = role.getPermissions() == null ? new HashSet<>()
                : role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toCollection(HashSet::new));

        Set<Permission> permissions = new HashSet<>();
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = permissionRepository.findAllById(request.getPermissionIds())
                    .stream().collect(Collectors.toSet());
        }

        if (!isPredefined) {
            role.setName(upperRequested);
        }
        role.setDescription(request.getDescription());
        role.setPermissions(permissions);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            role.setStatus(Status.valueOf(request.getStatus()));
        }

        roleRepository.save(role);
        auditPublisher.publishUpdate(AuditModule.PA, "Role", role.getId(),
                "Role actualizado: " + role.getName() + " (companyId=" + role.getCompanyId() + ")");

        // QA Bloque AV (HU-PA-11 E4 + HU-PA-12 E4, 2026-05-14): NO invalidar
        // la sesion. EffectivePermissionsFilter recomputa authorities en cada
        // request, asi los cambios al rol se reflejan en la siguiente accion
        // del usuario sin expulsarlo.

        // HU-PA-20 E2: notificar a TODOS los usuarios que tienen este rol asignado
        // con detalle de permisos agregados/removidos.
        try {
            if (notificationService != null) {
                Set<String> newCodes = permissions.stream().map(Permission::getCode).collect(Collectors.toCollection(HashSet::new));
                Set<String> added = new HashSet<>(newCodes); added.removeAll(previousCodes);
                Set<String> removed = new HashSet<>(previousCodes); removed.removeAll(newCodes);
                if (!added.isEmpty() || !removed.isEmpty()) {
                    List<User> affected = userRepository.findAllByRoles_IdAndDeletedAtIsNull(role.getId());
                    String body = "Su rol " + role.getName() + " fue modificado."
                            + (added.isEmpty() ? "" : " Agregados: " + added)
                            + (removed.isEmpty() ? "" : " Removidos: " + removed);
                    for (User u : affected) {
                        try {
                            // QA Bloque AV (Bug 4, 2026-05-14): NO incluir
                            // actionUrl en la notificacion de cambio de rol.
                            // El usuario reporto que clickear estas notifs
                            // lleva a 404 ya que la ruta /perfil no resuelve
                            // al destino correcto y depende del modulo. La
                            // HU pide que click "solo marque como leida"
                            // sin navegacion forzada.
                            notificationService.publishToUser(u.getId(),
                                com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                                    .companyId(u.getCompanyId())
                                    .eventKey("ROLE_PERMISSIONS_CHANGED")
                                    .title("Su rol " + role.getName() + " fue modificado")
                                    .body(body)
                                    .sourceId(role.getId())
                                    .sourceType("Role")
                                    .build());
                        } catch (RuntimeException ignored) { /* notif individual no rompe update */ }
                    }
                }
            }
        } catch (Exception ex) {
            // notificacion no debe romper el update
        }

        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol actualizado exitosamente"), Optional.of(toRequest(role)))
        );
    }

    /**
     * QA Bloque PA Bugs 10/11/12 (HU-PA-06 E2/E3/E4, 2026-05-09):
     * elimina un rol con motivo obligatorio (>=30 chars), bloqueo si tiene
     * usuarios asignados con datos de cuantos y quienes, y bloqueo
     * especial si el rol es el unico que da acceso a algun usuario.
     *
     * Acepta motivo via query param `?reason=...`. Si esta vacio o <30 chars
     * responde 400 con el texto literal de la HU. Si hay usuarios responde
     * 400 con cantidad + listado (ids/emails) + indicacion para reasignar.
     * Si algun usuario quedaria sin roles, mensaje literal HU-PA-06 E3.
     * Si el rol ADMIN_EMPRESA fuera el ultimo de la empresa, mensaje E5.
     */
    public ResponseEntity<?> deleteRole(Long id, String reason) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        // QA Bloque BC (2026-05-17): bloqueo cross-tenant. ADMIN_EMPRESA solo
        // puede eliminar roles de su empresa (PLATFORM_ADMIN bypass).
        assertRoleAccessible(role);

        if (role.getDeletedAt() != null) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("El rol ya se encuentra eliminado"))
            );
        }

        // HU-PA-06 E4: motivo obligatorio (>=30 chars).
        if (reason == null || reason.trim().length() < 30) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(
                    "Debe ingresar un motivo de eliminación de al menos 30 caracteres"))
            );
        }

        // HU-PA-06 E5: roles globales del sistema (PLATFORM_ADMIN, ADMIN, USER) NO se eliminan.
        if (Role.SYSTEM_GLOBAL_NAMES.contains(role.getName().toUpperCase())) {
            // QA Bloque PA Bug 78 (HU-PA-06 E7, 2026-05-11): registrar intento bloqueado.
            try {
                auditPublisher.publishUpdate(AuditModule.PA, "Role", role.getId(),
                    "ROLE_DELETE_BLOCKED snapshot: name=" + role.getName()
                    + " | motivoBloqueo=\"rol global del sistema\""
                    + " | reason=\"" + reason.trim() + "\"");
            } catch (Exception ignore) {}
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(
                    "No se pueden eliminar los roles globales del sistema."))
            );
        }

        // HU-PA-06 E5 (predefinido): si es predefinido y es el unico ADMIN_EMPRESA del tenant,
        // bloquear con mensaje literal HU.
        if ("ADMIN_EMPRESA".equalsIgnoreCase(role.getName())
                && role.getCompanyId() != null
                && userRepository.countOtherAdminEmpresaInCompany(role.getCompanyId(), role.getId()) == 0) {
            // QA Bloque PA Bug 78 (HU-PA-06 E7, 2026-05-11): registrar intento bloqueado.
            try {
                auditPublisher.publishUpdate(AuditModule.PA, "Role", role.getId(),
                    "ROLE_DELETE_BLOCKED snapshot: name=ADMIN_EMPRESA"
                    + " | companyId=" + role.getCompanyId()
                    + " | motivoBloqueo=\"ultimo ADMIN_EMPRESA de la empresa\""
                    + " | reason=\"" + reason.trim() + "\"");
            } catch (Exception ignore) {}
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(
                    "Debe existir al menos un ADMIN_EMPRESA activo en la empresa"))
            );
        }

        // HU-PA-06 E2/E3: si tiene usuarios asignados, devolver mensaje detallado.
        java.util.List<com.sigcon.backend.parametrization.users.domain.model.User> users =
                userRepository.findAllByRoles_IdAndDeletedAtIsNull(role.getId());

        if (!users.isEmpty()) {
            // HU-PA-06 E3: detectar si algun usuario perderia acceso (rol unico)
            long onlyThisRoleUsers = userRepository.countUsersWithOnlyThisRoleActive(role.getId());

            // Construir listado de usuarios afectados con datos para reasignar
            java.util.List<java.util.Map<String, Object>> affected = users.stream()
                .limit(10)
                .map(u -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("email", u.getEmail());
                    m.put("username", u.getUsername());
                    // QA Bloque PA Bug 77 (HU-PA-06 E2, 2026-05-11): la ruta
                    // real del modulo Usuarios es /parametrizacion/users (no
                    // /usuarios) y la edicion es via modal abierto por query
                    // param ?edit={id}. Antes apuntabamos a una ruta inexistente.
                    m.put("editUrl", "/parametrizacion/users?edit=" + u.getId());
                    return m;
                })
                .collect(Collectors.toList());

            String mainMsg;
            if (onlyThisRoleUsers > 0) {
                // HU-PA-06 E3 mensaje literal
                mainMsg = "No se puede eliminar el rol: " + onlyThisRoleUsers
                       + " usuario(s) perderian acceso total al sistema. "
                       + "Asigne otro rol a esos usuarios antes de eliminar.";
            } else {
                // HU-PA-06 E2 mensaje literal con detalles
                mainMsg = "No se puede eliminar el rol porque está asociado a "
                       + users.size() + " usuario(s) activos. "
                       + "Reasigne otro rol a los usuarios antes de eliminar.";
            }

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("success", false);
            body.put("code", 400);
            body.put("error", "Error en la operación");
            body.put("message", mainMsg);
            body.put("data", null);
            body.put("affectedUsersCount", users.size());
            body.put("usersWithOnlyThisRole", onlyThisRoleUsers);
            body.put("affectedUsers", affected);
            body.put("timestamp", LocalDateTime.now().toString());

            // QA Bloque PA Bug 78 (HU-PA-06 E7, 2026-05-11): registrar el intento
            // BLOQUEADO de eliminacion en auditoria. La HU exige trazabilidad de
            // CUALQUIER intento (exitoso o no) sobre roles del sistema. Antes solo
            // registrabamos cuando la eliminacion era exitosa.
            try {
                String blockedSnapshot = "ROLE_DELETE_BLOCKED snapshot: "
                    + "name=" + role.getName()
                    + " | id=" + role.getId()
                    + " | companyId=" + role.getCompanyId()
                    + " | reason=\"" + (reason == null ? "" : reason.trim()) + "\""
                    + " | motivoBloqueo=\"" + mainMsg + "\""
                    + " | affectedUsersCount=" + users.size()
                    + " | usersWithOnlyThisRole=" + onlyThisRoleUsers;
                auditPublisher.publishUpdate(AuditModule.PA, "Role", role.getId(), blockedSnapshot);
            } catch (Exception ignore) { /* defensivo: no romper la respuesta */ }
            return ResponseEntity.badRequest().body(body);
        }

        // HU-PA-06 E7 (QA Bloque PA Bug 14, 2026-05-09): snapshot completo del
        // rol ANTES de eliminar + motivo. Incluye nombre, descripcion, status,
        // companyId, lista de permission codes (no solo cuenta) y timestamp.
        // Esto cumple "snapshot completo del rol antes de eliminar y company_id"
        // exigido por la HU.
        java.util.List<String> permCodes = role.getPermissions() == null ? java.util.List.of()
            : role.getPermissions().stream()
                .map(p -> p.getCode() != null ? p.getCode() : p.getName())
                .sorted()
                .collect(Collectors.toList());

        StringBuilder snapshot = new StringBuilder();
        snapshot.append("ROLE_DELETED snapshot: ")
                .append("name=").append(role.getName())
                .append(" | id=").append(role.getId())
                .append(" | companyId=").append(role.getCompanyId())
                .append(" | status=").append(role.getStatus())
                .append(" | description=\"").append(role.getDescription() == null ? "" : role.getDescription()).append("\"")
                .append(" | permissions(").append(permCodes.size()).append(")=").append(permCodes)
                .append(" | createdAt=").append(role.getCreatedAt())
                .append(" | motivo=\"").append(reason.trim()).append("\"");

        role.setDeletedAt(LocalDateTime.now());
        roleRepository.save(role);
        auditPublisher.publishDelete(AuditModule.PA, "Role", role.getId(), snapshot.toString());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Rol eliminado exitosamente"), Optional.of(toRequest(role)))
        );
    }

    /** Compat: firma legacy para callers que aun no envien `reason`. */
    public ResponseEntity<?> deleteRole(Long id) {
        return deleteRole(id, null);
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

        // QA Bloque BC (2026-05-17): el usuario destino debe pertenecer al
        // tenant del caller (PLATFORM_ADMIN bypass).
        assertSameTenantUser(user);

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        // QA Bloque BC (2026-05-17): el rol asignado debe pertenecer al tenant
        // del caller. Previene asignar a un usuario un rol de otra empresa.
        assertRoleAccessible(role);

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
                    auditPublisher.publishCreate(AuditModule.PA, "Role", role.getId(), "Role creado id=" + role.getId());
                }
            }
    
            permissionRepository.save(permission);
            auditPublisher.publishCreate(AuditModule.PA, "Role", permission.getId(), "Role creado id=" + permission.getId());

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
            auditPublisher.publishUpdate(AuditModule.PA, "Role", permission.getId(), "Role actualizado id=" + permission.getId());
            
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

        // QA Bloque BC (2026-05-17): el `id` del rol es obligatorio.
        // Antes, si venia null se filtraba un mensaje tecnico de Spring Data
        // ("The given id must not be null"). Ahora se rechaza con mensaje
        // funcional.
        if (request == null || request.getId() == null) {
            throw new IllegalArgumentException("Debe especificar el id del rol.");
        }
        if (request.getPermissionIds() == null || request.getPermissionIds().isEmpty()) {
            throw new IllegalArgumentException("Debe especificar al menos un permiso a asignar.");
        }

        Role role = roleRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));

        // QA Bloque BC (2026-05-17): bloqueo cross-tenant. ADMIN_EMPRESA solo
        // puede asignar permisos a roles de su empresa (PLATFORM_ADMIN bypass).
        assertRoleAccessible(role);

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

        // QA Bloque AV (HU-PA-11 E4 + HU-PA-12 E4, 2026-05-14): NO invalidar
        // la sesion de los usuarios. El EffectivePermissionsFilter recomputa
        // sus authorities en cada request consultando BD, asi los nuevos
        // permisos surten efecto en la SIGUIENTE accion del usuario sin
        // expulsarlo de la sesion. Antes (Bug 96) se hacia
        // setSessionInvalidatedAt(now) lo cual obligaba a re-login -
        // comportamiento que la HU prohibe explicitamente.

        // Payload minimo para evitar errores de serializacion Hibernate proxy.
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", role.getId());
        payload.put("name", role.getName());
        payload.put("permissionCount", role.getPermissions() != null ? role.getPermissions().size() : 0);
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permisos asignados correctamente al rol"), Optional.of(payload))
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

        // QA Bloque BC (2026-05-17): validar request.getId() antes de tocar JPA
        // para evitar exponer "Transaction silently rolled back" al cliente.
        if (request == null || request.getId() == null) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe especificar el id del rol."))
            );
        }
        if (request.getPermissionIds() == null || request.getPermissionIds().isEmpty()) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe especificar al menos un permiso a remover."))
            );
        }

        try {
            Role role = roleRepository.findById(request.getId())
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

            // QA Bloque BC (2026-05-17): bloqueo cross-tenant. Antes del IF
            // (porque la TenantIsolationException debe propagar como 404).
            assertRoleAccessible(role);

            if (role.getPermissions().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of("El rol no tiene permisos asignados"))
                );
            }

            role.getPermissions().removeIf(
                    permission -> request.getPermissionIds().contains(permission.getId())
            );

            roleRepository.save(role);
            auditPublisher.publishDelete(AuditModule.PA, "Role", role.getId(), "Role eliminado id=" + role.getId());

            // QA Bloque AV (HU-PA-11 E4 + HU-PA-12 E4, 2026-05-14): NO invalidar
            // la sesion. EffectivePermissionsFilter recomputa authorities en
            // cada request, asi remover un permiso del rol surte efecto en la
            // siguiente accion del usuario sin expulsarlo.

            // Devolver payload minimo (no la entidad Role) para evitar errores
            // de serializacion Jackson con proxies lazy de Hibernate
            // (ByteBuddyInterceptor).
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("id", role.getId());
            payload.put("name", role.getName());
            payload.put("permissionCount", role.getPermissions() != null ? role.getPermissions().size() : 0);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Permisos removidos correctamente del rol"), Optional.of(payload))
            );

        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException tie) {
            // QA Bloque BC (2026-05-17): dejar propagar para que el
            // GlobalExceptionHandler la mapee a HTTP 404 "Recurso no encontrado".
            throw tie;
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

    /**
     * HU-PA-03 E1 (QA Bloque PA, 2026-05-09): mapea Role -> RoleRequest con
     * los campos extra que la HU exige en el listado:
     *   - description: campo nuevo de la entidad
     *   - type: PREDEFINED / CUSTOM / GLOBAL (calculado)
     *   - assignedUsersCount: COUNT(*) FROM users_roles WHERE role_id = ?
     *   - createdAt: formato ISO para mostrar fecha
     *   - companyId: para que el frontend pueda discriminar
     */
    private RoleRequest toRequest(Role role) {
        String type;
        if (role.getCompanyId() == null) {
            type = "GLOBAL";
        } else if (role.isPredefined()) {
            type = "PREDEFINED";
        } else {
            type = "CUSTOM";
        }
        Long usersCount = 0L;
        try {
            usersCount = roleRepository.countActiveUsersByRoleId(role.getId());
        } catch (Exception ignore) { /* defensivo: no romper el listado */ }

        // QA Bloque PA Bug 75 (HU-PA-03 E3, 2026-05-11): resolver companyName
        // legible para discriminar roles del mismo nombre entre empresas en la
        // vista cross-empresa de PLATFORM_ADMIN. Defensivo: si no hay company
        // (rol global) o el repo no esta inyectado, queda null.
        String companyName = null;
        if (role.getCompanyId() != null && companyRepository != null) {
            try {
                companyName = companyRepository.findById(role.getCompanyId())
                    .map(c -> c.getBusinessName())
                    .orElse(null);
            } catch (Exception ignore) { /* no romper el listado */ }
        }

        return RoleRequest.builder()
            .id(role.getId())
            .name(role.getName())
            .description(role.getDescription())
            .permissionIds(role.getPermissions().stream().map(Permission::getId).collect(Collectors.toSet()))
            .permissions(role.getPermissions().stream().map(this::toDTO).collect(Collectors.toList()))
            .status(role.getStatus().name())
            .type(type)
            .companyId(role.getCompanyId())
            .companyName(companyName)
            .assignedUsersCount(usersCount == null ? 0L : usersCount)
            .createdAt(role.getCreatedAt() == null ? null : role.getCreatedAt().toString())
            .version(role.getVersion())
            .build();
    }

    private String parseStatus(Status status) {
        return status.name();
    }

    /**
     * QA Bloque BC (2026-05-17): defensa multi-tenant en operaciones mutadoras
     * de roles. Antes de modificar/eliminar/asignar permisos a un rol, validar
     * que el rol pertenece al tenant del usuario actual.
     *
     * <p>Reglas:
     * <ul>
     *   <li>Si el rol es global ({@code companyId == null}) solo PLATFORM_ADMIN
     *       puede operar sobre el. ADMIN_EMPRESA recibe 404 (no revelar que el
     *       rol global existe).</li>
     *   <li>Si el rol tiene tenant y el caller es PLATFORM_ADMIN, bypass.</li>
     *   <li>Si el rol tiene tenant y el caller pertenece a otro tenant, 404.</li>
     *   <li>Si el tenant context es null (usuario sin empresa) y el rol tiene
     *       tenant: 404.</li>
     * </ul>
     *
     * @throws com.sigcon.backend.platform.tenant.TenantIsolationException
     *         que el {@code GlobalExceptionHandler} traduce a HTTP 404 con
     *         mensaje literal "Recurso no encontrado".
     */
    private void assertRoleAccessible(Role role) {
        if (role == null) return;
        final boolean isPlatform = com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin();
        if (isPlatform) return;

        final Long callerTenantId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        final Long roleTenantId = role.getCompanyId();

        // Rol global -> solo PLATFORM_ADMIN
        if (roleTenantId == null) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                "Rol global no accesible para usuario tenant (roleId=" + role.getId() + ")");
        }
        // Rol tenant -> debe coincidir con tenant del caller
        if (callerTenantId == null || !roleTenantId.equals(callerTenantId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                "Rol pertenece a otra empresa (roleId=" + role.getId()
                + ", roleTenant=" + roleTenantId + ", callerTenant=" + callerTenantId + ")");
        }
    }

    /**
     * QA Bloque BC (2026-05-17): defensa multi-tenant para asignacion de rol a
     * usuario. El usuario destino debe pertenecer al mismo tenant del caller.
     * PLATFORM_ADMIN tiene bypass.
     */
    private void assertSameTenantUser(User user) {
        if (user == null) return;
        final boolean isPlatform = com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin();
        if (isPlatform) return;

        final Long callerTenantId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        final Long userTenantId = user.getCompanyId();

        if (userTenantId == null || callerTenantId == null
                || !userTenantId.equals(callerTenantId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                "Usuario pertenece a otra empresa (userId=" + user.getId()
                + ", userTenant=" + userTenantId + ", callerTenant=" + callerTenantId + ")");
        }
    }
}

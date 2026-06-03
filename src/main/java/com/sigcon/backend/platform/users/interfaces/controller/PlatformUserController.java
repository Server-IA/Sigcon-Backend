package com.sigcon.backend.platform.users.interfaces.controller;

import com.sigcon.backend.platform.users.application.PlatformUserDTO;
import com.sigcon.backend.platform.users.application.ResetPasswordRequest;
import com.sigcon.backend.platform.users.domain.service.PlatformUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller cross-empresa para HU-PA-PLAT-04.
 *
 * <p><b>Alcance:</b> solo accesible para {@code PLATFORM_ADMIN}. Los admins de
 * empresa (rol tenant) reciben HTTP 403 aunque tengan {@code ROLE_ADMIN}.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code GET /api/platform/users} — listado cross-empresa con filtros
 *       opcionales: {@code companyId}, {@code platform=true}, {@code status}
 *   <li>{@code POST /api/platform/users/{id}/reset-password} — resetear
 *       contrasenia de cualquier usuario del sistema
 * </ul>
 *
 * <p>El {@code TenantFilterAspect} detecta el claim {@code PLATFORM_ADMIN}
 * en el JWT y NO habilita el filter de Hibernate, permitiendo ver usuarios
 * de todas las empresas.
 */
@RestController
@RequestMapping("/api/platform/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
@Tag(name = "Plataforma - Usuarios",
     description = "Gestion cross-empresa de usuarios (HU-PA-PLAT-04). Solo PLATFORM_ADMIN.")
public class PlatformUserController {

    private final PlatformUserService service;

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @GetMapping
    @Operation(summary = "Listar usuarios cross-empresa",
               description = "Devuelve usuarios de todas las empresas con filtros opcionales. "
                           + "Incluye companyName para identificar a que empresa pertenece cada uno.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista paginada de usuarios"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Usuario sin rol PLATFORM_ADMIN")
    })
    public ResponseEntity<Page<PlatformUserDTO>> list(
            @Parameter(description = "Filtrar por ID de empresa", example = "2")
            @RequestParam(required = false) Long companyId,
            @Parameter(description = "Si true, solo usuarios PLATFORM_ADMIN", example = "false")
            @RequestParam(required = false) Boolean platform,
            @Parameter(description = "Filtrar por estado (ACTIVE / INACTIVE)", example = "ACTIVE")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filtrar por nombre de rol asignado (case-insensitive)", example = "CONTADOR")
            @RequestParam(required = false) String roleName,
            @Parameter(description = "Pagina (base 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanio de pagina", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        // QA Bloque PA Bug 63 (HU-PA-PLAT-04 E3): roleName filter
        return ResponseEntity.ok(service.search(companyId, platform, status, roleName, pageable));
    }

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Resetear contrasenia de usuario",
               description = "Asigna una contrasenia temporal a un usuario de cualquier empresa. "
                           + "El usuario podra loguearse con la nueva contrasenia inmediatamente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrasenia reseteada"),
        @ApiResponse(responseCode = "400", description = "Usuario no encontrado o contrasenia invalida"),
        @ApiResponse(responseCode = "401", description = "Token ausente o invalido"),
        @ApiResponse(responseCode = "403", description = "Usuario sin rol PLATFORM_ADMIN")
    })
    public ResponseEntity<?> resetPassword(
            @Parameter(description = "ID del usuario a resetear", example = "15")
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest body) {
        service.resetPassword(id, body.getNewPassword());
        return ResponseEntity.ok(Map.of("success", true,
                "message", "Contrasenia reseteada correctamente"));
    }

    /**
     * QA Bloque PA Bug 65 (HU-PA-PLAT-07 E1, 2026-05-09): crear PLATFORM_ADMIN secundario.
     * Email unico globalmente (cross-tenant), platform_role=PLATFORM_ADMIN, company_id=NULL.
     */
    @PostMapping("/platform-admin")
    @Operation(summary = "HU-PA-PLAT-07 E1: crear PLATFORM_ADMIN secundario",
               description = "Crea un nuevo usuario de plataforma. Email unico globalmente. "
                           + "platform_role=PLATFORM_ADMIN, company_id=NULL.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "PLATFORM_ADMIN creado"),
        @ApiResponse(responseCode = "400", description = "Email duplicado, contrasenia invalida"),
        @ApiResponse(responseCode = "403", description = "No es PLATFORM_ADMIN")
    })
    public ResponseEntity<?> createPlatformAdmin(@RequestBody java.util.Map<String, Object> body) {
        try {
            PlatformUserDTO dto = service.createPlatformAdmin(
                (String) body.get("name"),
                (String) body.get("lastname"),
                (String) body.get("email"),
                (String) body.get("username"),
                (String) body.get("password"));
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "code", 400, "message", ex.getMessage()));
        }
    }

    /**
     * QA Bloque PA Bug 65 (HU-PA-PLAT-07 E3): editar PLATFORM_ADMIN. Permite cambiar
     * name/lastname/email pero NO el flag platform (eso requiere flujo distinto).
     */
    @org.springframework.web.bind.annotation.PutMapping("/platform-admin/{id}")
    @Operation(summary = "HU-PA-PLAT-07 E3: editar PLATFORM_ADMIN secundario")
    public ResponseEntity<?> updatePlatformAdmin(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        try {
            PlatformUserDTO dto = service.updatePlatformAdmin(id,
                (String) body.get("name"),
                (String) body.get("lastname"),
                (String) body.get("email"));
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "code", 400, "message", ex.getMessage()));
        }
    }

    /**
     * QA Bloque PA Bug 65 (HU-PA-PLAT-07 E4, 2026-05-09): desactivar PLATFORM_ADMIN
     * con safeguard. Si es el unico activo, bloquea con HTTP 409.
     */
    /**
     * PA-RF-PLAT-07 v3.0 (Control de Cambios PA, 2026-05-29): consultar un
     * PLATFORM_ADMIN / usuario por id (operacion de consulta del ciclo de vida).
     */
    @GetMapping("/platform-admin/{id}")
    @Operation(summary = "PA-RF-PLAT-07: consultar usuario/PLATFORM_ADMIN por id")
    public ResponseEntity<?> getPlatformAdmin(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getPlatformAdmin(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "code", 400, "message", ex.getMessage()));
        }
    }

    /**
     * PA-RF-PLAT-07 v3.0: reactivar un PLATFORM_ADMIN desactivado. El motivo es
     * opcional al activar.
     */
    @PostMapping("/platform-admin/{id}/activate")
    @Operation(summary = "PA-RF-PLAT-07: reactivar PLATFORM_ADMIN secundario")
    public ResponseEntity<?> activatePlatformAdmin(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String reason = body == null ? null : (String) body.get("reason");
        try {
            return ResponseEntity.ok(service.activatePlatformAdmin(id,
                    reason == null || reason.isBlank() ? "(sin motivo)" : reason.trim()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "code", 400, "message", ex.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/platform-admin/{id}")
    @Operation(summary = "HU-PA-PLAT-07 E4: desactivar PLATFORM_ADMIN secundario",
               description = "Bloquea con HTTP 409 si seria el ultimo PLATFORM_ADMIN activo. "
                           + "PA-RF-PLAT-07 v3.0: requiere motivo (minimo 10 caracteres).")
    public ResponseEntity<?> deactivatePlatformAdmin(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        // PA-RF-PLAT-07 v3.0: motivo obligatorio para desactivar.
        String reason = body == null ? null : (String) body.get("reason");
        if (reason == null || reason.trim().length() < 10) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "code", 400,
                "message", "El motivo de desactivacion es obligatorio (minimo 10 caracteres)."));
        }
        try {
            service.deactivatePlatformAdmin(id, reason.trim());
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "PLATFORM_ADMIN desactivado"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(Map.of(
                "success", false, "code", 409, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "code", 400, "message", ex.getMessage()));
        }
    }
}

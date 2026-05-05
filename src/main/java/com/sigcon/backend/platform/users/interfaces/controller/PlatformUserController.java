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
            @Parameter(description = "Pagina (base 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanio de pagina", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return ResponseEntity.ok(service.search(companyId, platform, status, pageable));
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
}

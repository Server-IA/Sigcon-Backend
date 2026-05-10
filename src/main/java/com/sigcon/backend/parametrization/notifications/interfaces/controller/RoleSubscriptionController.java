package com.sigcon.backend.parametrization.notifications.interfaces.controller;

import com.sigcon.backend.parametrization.notifications.application.UpsertRoleSubscriptionRequest;
import com.sigcon.backend.parametrization.notifications.domain.service.RoleSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-PA-18: configuracion de eventos suscritos por rol.
 *
 * <p>Solo ADMIN_EMPRESA / ADMIN / PLATFORM_ADMIN: la suscripcion vive a nivel rol y
 * afecta a todos los usuarios que tengan ese rol.
 */
@RestController
@RequestMapping("/api/parametrization/roles/{roleId}/notification-subscriptions")
@RequiredArgsConstructor
@Tag(name = "Suscripciones de rol (Sprint 4)", description = "HU-PA-18 configuracion notificaciones por rol")
public class RoleSubscriptionController {

    private final RoleSubscriptionService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PARAMETRIZACION.ROLES.EDITAR') or hasAuthority('PERM_PAR.ROLES.EDITAR') or hasAuthority('PERM_PAR.NOTIFICACIONES.CONFIGURAR_ROL') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN') or hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-18: lista suscripciones del rol")
    public ResponseEntity<?> list(@PathVariable Long roleId) {
        return ResponseEntity.ok(Map.of("data", service.listForRole(roleId)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_PARAMETRIZACION.ROLES.EDITAR') or hasAuthority('PERM_PAR.ROLES.EDITAR') or hasAuthority('PERM_PAR.NOTIFICACIONES.CONFIGURAR_ROL') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN') or hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-18: crea o actualiza una suscripcion del rol")
    public ResponseEntity<?> upsert(@PathVariable Long roleId, @Valid @RequestBody UpsertRoleSubscriptionRequest req) {
        try {
            return ResponseEntity.ok(service.upsert(roleId, req));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/{eventKey}")
    @PreAuthorize("hasAuthority('PERM_PARAMETRIZACION.ROLES.EDITAR') or hasAuthority('PERM_PAR.ROLES.EDITAR') or hasAuthority('PERM_PAR.NOTIFICACIONES.CONFIGURAR_ROL') or hasAuthority('ROLE_ADMIN_EMPRESA') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN') or hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-18: elimina una suscripcion (soft delete)")
    public ResponseEntity<?> delete(@PathVariable Long roleId, @PathVariable String eventKey) {
        boolean ok = service.delete(roleId, eventKey);
        if (!ok) return ResponseEntity.status(404).body(Map.of("success", false,
                "message", "Suscripcion no encontrada"));
        return ResponseEntity.ok(Map.of("success", true));
    }
}

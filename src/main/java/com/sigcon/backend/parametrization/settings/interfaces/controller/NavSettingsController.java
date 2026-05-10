package com.sigcon.backend.parametrization.settings.interfaces.controller;

import com.sigcon.backend.parametrization.settings.domain.service.NavSettingsService;
import com.sigcon.backend.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * HU-PA-NAV-01: orden de modulos del sidebar por empresa.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/parametrization/nav-settings/module-order} — leer orden actual.</li>
 *   <li>{@code PUT /api/parametrization/nav-settings/module-order} — persistir orden.</li>
 *   <li>{@code DELETE /api/parametrization/nav-settings/module-order} — reset a default.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/parametrization/nav-settings")
@RequiredArgsConstructor
@Tag(name = "Parametrizacion - Navegacion",
     description = "HU-PA-NAV-01: configurar orden de modulos en sidebar")
public class NavSettingsController {

    private final NavSettingsService service;

    @GetMapping("/module-order")
    @PreAuthorize("hasAuthority('PERM_PAR.NAVEGACION.EDITAR') or hasAuthority('ROLE_ADMIN_EMPRESA')"
                + " or hasAuthority('ROLE_ADMIN') or hasAuthority('PLATFORM_ADMIN')")
    @Operation(summary = "Obtener orden actual de modulos para mi empresa")
    public ResponseEntity<?> getOrder() {
        Long tenant = TenantContext.getCompanyId();
        if (tenant == null) {
            return ResponseEntity.ok(Map.of("data", List.of(),
                    "message", "Sin empresa activa: orden default"));
        }
        return ResponseEntity.ok(Map.of("data", service.getOrder(tenant)));
    }

    @PutMapping("/module-order")
    @PreAuthorize("hasAuthority('PERM_PAR.NAVEGACION.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-NAV-01 E3: guardar orden de modulos")
    public ResponseEntity<?> putOrder(@RequestBody Map<String, Object> body) {
        // QA Bloque PA Bug 69 (HU-PA-NAV-01 E3, 2026-05-09): aceptar 'order' o
        // 'moduleOrder' (ambos nombres usados por la HU + clientes legacy).
        Object orderObj = body.get("order");
        if (orderObj == null) orderObj = body.get("moduleOrder");
        if (!(orderObj instanceof List<?> list)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "El campo 'order' (o 'moduleOrder') es obligatorio y debe ser un array de IDs"));
        }
        List<Long> ordered = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) ordered.add(n.longValue());
            else {
                try { ordered.add(Long.parseLong(String.valueOf(o))); }
                catch (NumberFormatException ex) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "Los IDs deben ser numericos"));
                }
            }
        }
        try {
            List<Long> saved = service.saveOrder(ordered);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Orden guardado correctamente",
                    "data", saved));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/module-order")
    @PreAuthorize("hasAuthority('PERM_PAR.NAVEGACION.EDITAR') or hasAuthority('ROLE_ADMIN_EMPRESA')"
                + " or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Reset al orden de modulos por defecto del sistema")
    public ResponseEntity<?> resetOrder() {
        try {
            service.resetOrder();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Orden reseteado a default"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", ex.getMessage()));
        }
    }
}

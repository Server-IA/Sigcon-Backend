package com.sigcon.backend.parametrization.settings.interfaces.controller;

import com.sigcon.backend.parametrization.settings.domain.service.BrandConfigService;
import com.sigcon.backend.platform.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-PA-BRAND-01: identidad visual de la empresa.
 *
 * <p>El branding es por empresa (tenant). El frontend resuelve por
 * {@code TenantContext.getCompanyId()} del JWT del usuario actual (E7).
 *
 * <ul>
 *   <li>{@code GET /api/parametrization/brand-config} — leer la config actual.</li>
 *   <li>{@code PUT /api/parametrization/brand-config} — guardar (E1+E2+E3).</li>
 *   <li>{@code DELETE /api/parametrization/brand-config} — reset al theme default (E5).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/parametrization/brand-config")
@RequiredArgsConstructor
@Tag(name = "Parametrizacion - Identidad Visual",
     description = "HU-PA-BRAND-01: configurar colores, logo, favicon y nombre comercial por empresa")
public class BrandConfigController {

    private final BrandConfigService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PAR.IDENTIDAD_VISUAL.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Obtener identidad visual de la empresa actual")
    public ResponseEntity<?> getBrand() {
        Long tenant = TenantContext.getCompanyId();
        if (tenant == null) {
            return ResponseEntity.ok(Map.of("data", Map.of(),
                    "message", "Sin empresa activa: theme default"));
        }
        return ResponseEntity.ok(Map.of("data", service.get(tenant)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_PAR.IDENTIDAD_VISUAL.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "HU-PA-BRAND-01 E1+E2+E3: guardar identidad visual con validaciones")
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> saved = service.save(body);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Identidad visual actualizada correctamente",
                    "data", saved));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", ex.getMessage()));
        }
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('PERM_PAR.IDENTIDAD_VISUAL.EDITAR')"
                + " or hasAuthority('ROLE_ADMIN_EMPRESA')"
                + " or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "HU-PA-BRAND-01 E5: reset a theme default de SIGCON")
    public ResponseEntity<?> reset() {
        try {
            service.reset();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Identidad visual reseteada al default"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", ex.getMessage()));
        }
    }
}

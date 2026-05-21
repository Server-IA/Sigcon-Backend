package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.matching.application.ReglaClasificacionRequest;
import com.sigcon.backend.banks.matching.domain.service.ReglaClasificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** BNK-HU-071: CRUD de reglas de clasificación. */
@RestController
@RequestMapping("/api/v1/banks/reglas-clasificacion")
@RequiredArgsConstructor
@Tag(name = "BNK - Reglas de clasificación (HU-071)",
     description = "Catálogo de reglas para el pre-procesamiento de movimientos")
public class ReglaClasificacionController {

    private final ReglaClasificacionService service;

    @Operation(summary = "Listar reglas de clasificación")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(service.list());
    }

    @Operation(summary = "Crear regla de clasificación")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("")
    public ResponseEntity<?> create(@Valid @RequestBody ReglaClasificacionRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @Operation(summary = "Actualizar regla de clasificación")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody ReglaClasificacionRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @Operation(summary = "Activar/desactivar regla (BNK-HU-071 E2)")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean activa = !(body.get("activa") instanceof Boolean) || (Boolean) body.get("activa");
        return ResponseEntity.ok(service.toggle(id, activa));
    }

    @Operation(summary = "Probar regla contra movimientos históricos (BNK-HU-071 E3)")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/test")
    public ResponseEntity<?> test(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.test(body.get("patronRegex")));
    }
}

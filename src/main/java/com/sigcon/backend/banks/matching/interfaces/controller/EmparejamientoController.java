package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.matching.application.ManualMatchRequest;
import com.sigcon.backend.banks.matching.domain.service.EmparejamientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** BNK-HU-070: emparejamiento manual agrupado (N:1, 1:N, N:M) + preview + detalle + deshacer. */
@RestController
@RequestMapping("/api/v1/banks/emparejamientos")
@RequiredArgsConstructor
@Tag(name = "BNK - Emparejamientos manuales (HU-070)",
     description = "Selección múltiple y emparejamiento agrupado de movimientos de conciliación")
public class EmparejamientoController {

    private final EmparejamientoService service;

    @Operation(summary = "Preview de la selección: sumas y diferencia (BNK-HU-070 E1)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/preview")
    public ResponseEntity<?> preview(@Valid @RequestBody ManualMatchRequest req) {
        return ResponseEntity.ok(service.preview(req));
    }

    @Operation(summary = "Crear emparejamiento manual N:1 / 1:N / N:M (BNK-HU-070 E2-E5)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody ManualMatchRequest req) {
        return ResponseEntity.ok(service.createManual(req));
    }

    @Operation(summary = "Detalle del emparejamiento agregado (BNK-HU-070 E6)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return ResponseEntity.ok(service.detail(id));
    }

    @Operation(summary = "Confirmar una sugerencia (BNK-HU-069 E8)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<?> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(service.confirm(id));
    }

    @Operation(summary = "Deshacer/rechazar todo el emparejamiento como un bloque (BNK-HU-070 E6 / HU-069 E8)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> undo(@PathVariable Long id) {
        return ResponseEntity.ok(service.undo(id));
    }

    @Operation(summary = "Listar emparejamientos de la cuenta (workspace)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/cuenta/{bankAccountId}")
    public ResponseEntity<?> list(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.listForAccount(bankAccountId));
    }

    @Operation(summary = "Datos del workspace de conciliación (movimientos libres + emparejamientos)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/workspace/{bankAccountId}")
    public ResponseEntity<?> workspace(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.getWorkspace(bankAccountId));
    }
}

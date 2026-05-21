package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.matching.application.ParametrosMatchingRequest;
import com.sigcon.backend.banks.matching.domain.service.ParametrosMatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** BNK-HU-072: parámetros del motor de matching. */
@RestController
@RequestMapping("/api/v1/banks/parametros-matching")
@RequiredArgsConstructor
@Tag(name = "BNK - Parámetros de matching (HU-072)",
     description = "Tolerancias y umbrales del motor de conciliación (global + por cuenta)")
public class ParametrosMatchingController {

    private final ParametrosMatchingService service;

    @Operation(summary = "Parámetros globales de la empresa")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/global")
    public ResponseEntity<?> global() {
        return ResponseEntity.ok(service.getGlobal());
    }

    @Operation(summary = "Vista comparativa global vs cuenta vs efectivo (BNK-HU-072 E4)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/comparative")
    public ResponseEntity<?> comparative(@RequestParam(required = false) Long bankAccountId) {
        return ResponseEntity.ok(service.comparative(bankAccountId));
    }

    @Operation(summary = "Crear/actualizar parámetros (global si cuentaBancariaId nulo)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("")
    public ResponseEntity<?> upsert(@RequestBody ParametrosMatchingRequest req) {
        return ResponseEntity.ok(service.upsert(req));
    }
}

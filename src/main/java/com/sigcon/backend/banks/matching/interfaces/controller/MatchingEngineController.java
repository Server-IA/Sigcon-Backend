package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.matching.domain.service.MatchingEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** BNK-HU-069: motor de matching de conciliación bancaria (5 fases con score). */
@RestController
@RequestMapping("/api/v1/banks/matching")
@RequiredArgsConstructor
@Tag(name = "BNK - Motor de matching (HU-069)",
     description = "Comparación automática extracto vs libros en 5 fases con score 0-100")
public class MatchingEngineController {

    private final MatchingEngineService service;

    @Operation(summary = "Ejecutar el motor de matching sobre una cuenta (BNK-HU-069 E1-E10)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/ejecutar/{bankAccountId}")
    public ResponseEntity<?> run(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.runEngine(bankAccountId));
    }

    @Operation(summary = "Ejecutar el motor acotado a una sesión de conciliación (Paso 4)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/ejecutar-sesion/{sesionId}")
    public ResponseEntity<?> runForSession(@PathVariable Long sesionId) {
        return ResponseEntity.ok(service.runEngineForSession(sesionId));
    }
}

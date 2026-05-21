package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.matching.domain.service.PreprocessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** BNK-HU-068: pre-procesamiento de movimientos + corrección manual de clasificación. */
@RestController
@RequestMapping("/api/v1/banks/preprocesamiento")
@RequiredArgsConstructor
@Tag(name = "BNK - Pre-procesamiento (HU-068)",
     description = "Normalización, extracción de referencias y clasificación por reglas")
public class PreprocessingController {

    private final PreprocessingService service;

    @Operation(summary = "Pre-procesar movimientos de una cuenta (BNK-HU-068 E1)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/cuenta/{bankAccountId}")
    public ResponseEntity<?> preprocess(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.preprocessAccount(bankAccountId));
    }

    @Operation(summary = "Listar movimientos con su clasificación (BNK-HU-068 UI)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/cuenta/{bankAccountId}/movimientos")
    public ResponseEntity<?> listClassified(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.listClassified(bankAccountId));
    }

    @Operation(summary = "Corregir clasificación de un movimiento (BNK-HU-068 E8/E10)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PutMapping("/movimiento/{movementId}/clasificacion")
    public ResponseEntity<?> correct(@PathVariable Long movementId, @RequestBody Map<String, String> body) {
        var m = service.correctClassification(
                movementId, body.get("tipoMovimiento"), body.get("cuentaPucSugerida"));
        // Respuesta limpia (no se serializa la entidad con proxies lazy).
        Map<String, Object> r = new java.util.HashMap<>();
        r.put("id", m.getId());
        r.put("tipoMovimiento", m.getTipoMovimiento());
        r.put("clasificacionConfianza", m.getClasificacionConfianza());
        r.put("cuentaPucSugerida", m.getCuentaPucSugerida());
        return ResponseEntity.ok(r);
    }
}

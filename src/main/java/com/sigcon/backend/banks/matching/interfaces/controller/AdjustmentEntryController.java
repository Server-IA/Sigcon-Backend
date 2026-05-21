package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.matching.application.GenerateAdjustmentRequest;
import com.sigcon.backend.banks.matching.application.GenerateBatchAdjustmentRequest;
import com.sigcon.backend.banks.matching.domain.service.AdjustmentEntryService;
import com.sigcon.backend.banks.matching.domain.service.PartidaConciliatoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * BNK-HU-073: generación de comprobantes de ajuste de conciliación + gestión de
 * partidas conciliatorias (PENDIENTE -> RESUELTA_AJUSTE).
 */
@RestController
@RequestMapping("/api/v1/banks/ajustes")
@RequiredArgsConstructor
@Tag(name = "BNK - Ajustes de conciliación (HU-073)",
     description = "Genera comprobantes de ajuste en BORRADOR para partidas conciliatorias")
public class AdjustmentEntryController {

    private final AdjustmentEntryService adjustmentService;
    private final PartidaConciliatoriaService partidaService;

    @Operation(summary = "Preview del asiento de ajuste propuesto (BNK-HU-073 E1/E2)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/preview")
    public ResponseEntity<?> preview(@Valid @RequestBody GenerateAdjustmentRequest req) {
        return ResponseEntity.ok(adjustmentService.preview(req));
    }

    @Operation(summary = "Generar comprobante de ajuste en BORRADOR (BNK-HU-073 E3-E9)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/generar")
    public ResponseEntity<?> generate(@Valid @RequestBody GenerateAdjustmentRequest req) {
        return ResponseEntity.ok(adjustmentService.generate(req));
    }

    @Operation(summary = "Generar ajustes en lote: UNICO o INDIVIDUAL (BNK-HU-073 E6)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/generar-lote")
    public ResponseEntity<?> generateBatch(@Valid @RequestBody GenerateBatchAdjustmentRequest req) {
        return ResponseEntity.ok(adjustmentService.generateBatch(req));
    }

    @Operation(summary = "Listar partidas conciliatorias de la cuenta (BNK-HU-073 / HU-061)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/partidas/{bankAccountId}")
    public ResponseEntity<?> partidas(@PathVariable Long bankAccountId,
                                      @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(partidaService.listForAccount(bankAccountId, estado));
    }

    @Operation(summary = "Detectar/marcar partidas conciliatorias PENDIENTE (BNK-HU-061 E1)")
    @PreAuthorize("hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/partidas/{bankAccountId}/detectar")
    public ResponseEntity<?> detectar(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(partidaService.ensureCandidatesForAccount(bankAccountId));
    }
}

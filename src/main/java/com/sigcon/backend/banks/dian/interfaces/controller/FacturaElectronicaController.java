package com.sigcon.backend.banks.dian.interfaces.controller;

import com.sigcon.backend.banks.dian.domain.service.FacturaElectronicaCruceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BNK-HU-078: cruce de movimientos del extracto con facturas electrónicas (CxC/CxP)
 * y reporte de cumplimiento DIAN.
 */
@RestController
@RequestMapping("/api/v1/banks/cruce-fe")
@RequiredArgsConstructor
@Tag(name = "BNK - Cruce factura electrónica DIAN (HU-078)",
     description = "Conciliación de movimientos bancarios contra facturas electrónicas (art. 616-1 ET)")
public class FacturaElectronicaController {

    private final FacturaElectronicaCruceService service;
    private static final String VER = "hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";
    private static final String EDITAR = "hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";

    @Operation(summary = "Sugerir facturas coincidentes para un movimiento (BNK-HU-078 E1/E2)")
    @PreAuthorize(VER)
    @GetMapping("/sugerir/{movementId}")
    public ResponseEntity<?> sugerir(@PathVariable Long movementId) {
        return ResponseEntity.ok(service.sugerir(movementId));
    }

    @Operation(summary = "Aplicar cruce 1:1 movimiento↔factura (cobro/pago, parcial) (BNK-HU-078 E3/E4/E5/E8)")
    @PreAuthorize(EDITAR)
    @PostMapping("/aplicar")
    public ResponseEntity<?> aplicar(@RequestBody AplicarCruceRequest req) {
        return ResponseEntity.ok(service.aplicarCruce(req.getMovementId(), req.getInvoiceId()));
    }

    @Operation(summary = "Aplicar un movimiento a varias facturas (UNO_A_N) (BNK-HU-078 E6)")
    @PreAuthorize(EDITAR)
    @PostMapping("/aplicar-multiple")
    public ResponseEntity<?> aplicarMultiple(@RequestBody AplicarMultipleRequest req) {
        return ResponseEntity.ok(service.aplicarCruceMultiple(req.getMovementId(), req.getInvoiceIds()));
    }

    @Operation(summary = "Reporte de cumplimiento facturación electrónica vs cobros (BNK-HU-078 E7)")
    @PreAuthorize(VER)
    @GetMapping("/reporte-cumplimiento")
    public ResponseEntity<?> reporte(@RequestParam int year, @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(service.reporteCumplimiento(year, month));
    }

    @Data
    public static class AplicarCruceRequest {
        private Long movementId;
        private Long invoiceId;
    }

    @Data
    public static class AplicarMultipleRequest {
        private Long movementId;
        private List<Long> invoiceIds;
    }
}

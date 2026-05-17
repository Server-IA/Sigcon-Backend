package com.sigcon.backend.general.accounting.closing.interfaces.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.general.accounting.closing.application.AnnualClosingRequest;
import com.sigcon.backend.general.accounting.closing.application.ClosingRequest;
import com.sigcon.backend.general.accounting.closing.application.OpeningRequest;
import com.sigcon.backend.general.accounting.closing.domain.service.ClosingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para el cierre contable.
 * Gestiona cierre mensual, anual y generacion de asientos de apertura.
 * Operaciones irreversibles que cierran periodos contables.
 */
@RestController
@RequestMapping("/api/v1/cg/closing")
@RequiredArgsConstructor
@Tag(name = "9. Contabilidad General - Cierre Contable",
     description = "Endpoints para cierre mensual, anual y generacion de asientos de apertura")
@SecurityRequirement(name = "bearerAuth")
public class ClosingController {

    private final ClosingService closingService;

    // ─────────────────────────────────────────────────────
    // Cierre mensual
    // ─────────────────────────────────────────────────────

    @PostMapping("/monthly")
    @Operation(
            summary = "Cierre mensual",
            description = "Ejecuta el cierre contable mensual. "
                    + "Genera un asiento que cierra las cuentas de resultado (clases 4, 5, 6, 7) "
                    + "y transfiere el resultado neto a patrimonio. Cierra el periodo contable."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cierre mensual ejecutado correctamente"),
            @ApiResponse(responseCode = "400", description = "El periodo ya esta cerrado, tiene asientos en borrador "
                    + "o ya existe un cierre previo"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    // QA Bloque BD (2026-05-17): code real en BD es CG.CIERRES.EJECUTAR_MENSUAL
    @PreAuthorize("hasAnyAuthority('PERM_EXECUTE_CLOSING','TEMP_PERM_EXECUTE_CLOSING','TEMP_EXECUTE_CLOSING','PERM_CG.CIERRES.EJECUTAR_MENSUAL','TEMP_PERM_CG.CIERRES.EJECUTAR_MENSUAL','TEMP_CG.CIERRES.EJECUTAR_MENSUAL','PERM_CG.CIERRES.EJECUTAR','TEMP_PERM_CG.CIERRES.EJECUTAR','TEMP_CG.CIERRES.EJECUTAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> monthlyClosing(
            @Valid @RequestBody ClosingRequest request,
            Authentication authentication) {
        try {
            String createdBy = authentication != null ? authentication.getName() : "SYSTEM";
            return closingService.generateMonthlyClosing(
                    request.getYear(), request.getMonth(), request.getNotes(), createdBy);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Preview de cierre mensual
    // ─────────────────────────────────────────────────────

    @PostMapping("/monthly/preview")
    @Operation(
            summary = "Preview de cierre mensual",
            description = "Genera una previsualizacion del asiento de cierre mensual sin ejecutarlo. "
                    + "Permite al usuario revisar las lineas antes de confirmar."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preview generado correctamente"),
            @ApiResponse(responseCode = "400", description = "El periodo tiene asientos en borrador"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> monthlyClosingPreview(@Valid @RequestBody ClosingRequest request) {
        try {
            return closingService.previewMonthlyClosing(request.getYear(), request.getMonth());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Cierre anual
    // ─────────────────────────────────────────────────────

    @PostMapping("/annual")
    @Operation(
            summary = "Cierre anual",
            description = "Ejecuta el cierre contable anual. "
                    + "Cierra todos los meses abiertos del anio y genera un asiento "
                    + "de cierre anual consolidado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cierre anual ejecutado correctamente"),
            @ApiResponse(responseCode = "400", description = "Ya existe cierre anual, hay asientos en borrador "
                    + "en algun mes del anio"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    // QA Bloque BD (2026-05-17): code real en BD es CG.CIERRES.EJECUTAR_ANUAL
    @PreAuthorize("hasAnyAuthority('PERM_EXECUTE_CLOSING','TEMP_PERM_EXECUTE_CLOSING','TEMP_EXECUTE_CLOSING','PERM_CG.CIERRES.EJECUTAR_ANUAL','TEMP_PERM_CG.CIERRES.EJECUTAR_ANUAL','TEMP_CG.CIERRES.EJECUTAR_ANUAL','PERM_CG.CIERRES.EJECUTAR','TEMP_PERM_CG.CIERRES.EJECUTAR','TEMP_CG.CIERRES.EJECUTAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> annualClosing(
            @Valid @RequestBody AnnualClosingRequest request,
            Authentication authentication) {
        try {
            String createdBy = authentication != null ? authentication.getName() : "SYSTEM";
            return closingService.generateAnnualClosing(request.getYear(), request.getNotes(), createdBy);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Asiento de apertura
    // ─────────────────────────────────────────────────────

    @PostMapping("/opening")
    @Operation(
            summary = "Asiento de apertura",
            description = "Genera el asiento de apertura para un nuevo anio fiscal. "
                    + "Toma los saldos de cuentas de balance (clases 1, 2, 3) del cierre "
                    + "del anio anterior y los registra como apertura."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asiento de apertura generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Ya existe asiento de apertura o no hay saldos del anio anterior"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_EXECUTE_CLOSING','TEMP_PERM_EXECUTE_CLOSING','TEMP_EXECUTE_CLOSING','PERM_CG.CIERRES.EJECUTAR','TEMP_PERM_CG.CIERRES.EJECUTAR','TEMP_CG.CIERRES.EJECUTAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> openingEntry(
            @Valid @RequestBody OpeningRequest request,
            Authentication authentication) {
        try {
            String createdBy = authentication != null ? authentication.getName() : "SYSTEM";
            return closingService.generateOpeningEntry(request.getYear(), request.getNotes(), createdBy);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

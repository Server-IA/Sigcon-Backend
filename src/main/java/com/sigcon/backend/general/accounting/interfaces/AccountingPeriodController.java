package com.sigcon.backend.general.accounting.interfaces;

import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.application.AccountingPeriodDTO;
import com.sigcon.backend.general.accounting.application.ClosePeriodRequest;
import com.sigcon.backend.general.accounting.application.CreatePeriodRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la gestion del ciclo de vida de periodos contables.
 * Permite crear, consultar, cerrar, bloquear y reabrir periodos.
 */
@RestController
@RequestMapping("/api/v1/accounting-periods")
@RequiredArgsConstructor
@Tag(name = "Periodos Contables", description = "Gestion del ciclo de vida de periodos contables")
@SecurityRequirement(name = "bearerAuth")
public class AccountingPeriodController {

    private final AccountingPeriodService periodService;

    @PostMapping("/store")
    @Operation(summary = "Crear periodo contable", description = "Crea un nuevo periodo contable en estado OPEN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Periodo creado correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validacion o periodo duplicado"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> store(@Valid @RequestBody CreatePeriodRequest request) {
        try {
            AccountingPeriodDTO dto = periodService.createPeriod(request);
            return ResponseEntity.ok(Map.of("message", "Periodo creado correctamente", "data", dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Listar periodos contables", description = "Retorna todos los periodos contables registrados.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<List<AccountingPeriodDTO>> list() {
        return ResponseEntity.ok(periodService.getAllPeriods());
    }

    @GetMapping("/{year}")
    @Operation(summary = "Listar periodos por anio", description = "Retorna los periodos contables de un anio especifico.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<List<AccountingPeriodDTO>> listByYear(@PathVariable Integer year) {
        return ResponseEntity.ok(periodService.getPeriodsByYear(year));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Cerrar periodo contable", description = "Cambia el estado del periodo de OPEN a CLOSED.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Periodo cerrado correctamente"),
        @ApiResponse(responseCode = "400", description = "El periodo no esta en estado OPEN"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Periodo no encontrado")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> close(
            @PathVariable Long id,
            @RequestBody(required = false) ClosePeriodRequest request,
            Authentication authentication) {
        try {
            String closedBy = authentication.getName();
            String notes = (request != null) ? request.getNotes() : null;
            AccountingPeriodDTO dto = periodService.closePeriod(id, closedBy, notes);
            return ResponseEntity.ok(Map.of("message", "Periodo cerrado correctamente", "data", dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/lock")
    @Operation(summary = "Bloquear periodo contable", description = "Cambia el estado del periodo de CLOSED a LOCKED. Bloqueo permanente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Periodo bloqueado correctamente"),
        @ApiResponse(responseCode = "400", description = "El periodo no esta en estado CLOSED"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Periodo no encontrado")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> lock(@PathVariable Long id, Authentication authentication) {
        try {
            String lockedBy = authentication.getName();
            AccountingPeriodDTO dto = periodService.lockPeriod(id, lockedBy);
            return ResponseEntity.ok(Map.of("message", "Periodo bloqueado correctamente", "data", dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Reabrir periodo contable", description = "Cambia el estado del periodo de CLOSED a OPEN. No aplica para periodos bloqueados.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Periodo reabierto correctamente"),
        @ApiResponse(responseCode = "400", description = "El periodo no se puede reabrir"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Periodo no encontrado")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> reopen(@PathVariable Long id) {
        try {
            AccountingPeriodDTO dto = periodService.reopenPeriod(id);
            return ResponseEntity.ok(Map.of("message", "Periodo reabierto correctamente", "data", dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

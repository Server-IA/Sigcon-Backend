package com.sigcon.backend.lists_accounting.accounting_lists.interfaces;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.lists_accounting.accounting_lists.application.PucValidationReportDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.service.ChartOfAccountService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para el reporte de validacion masiva del PUC (HU-CG-09D).
 *
 * Expone el endpoint que dispara una verificacion integral de consistencia
 * sobre el Plan Unico de Cuentas: jerarquia correcta, naturaleza coherente,
 * codigos duplicados y cuentas inactivas con movimientos.
 */
@RestController
@RequestMapping("/api/v1/cg/puc")
@RequiredArgsConstructor
@Tag(name = "9. Contabilidad General - Validacion PUC",
     description = "Endpoint para validacion masiva de consistencia del Plan Unico de Cuentas (HU-CG-09D)")
@SecurityRequirement(name = "bearerAuth")
public class PucValidationController {

    private final ChartOfAccountService chartOfAccountService;

    /**
     * Genera el reporte de validacion masiva del PUC (HU-CG-09D).
     *
     * Detecta cuentas huerfanas, con naturaleza incoherente, codigos
     * duplicados y cuentas INACTIVE con movimientos en journal_entry_lines.
     *
     * @return reporte consolidado con totales y lista detallada de inconsistencias
     */
    @GetMapping("/validation-report")
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING_ACCOUNT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Reporte de validacion masiva del PUC (HU-CG-09D)",
            description = "Ejecuta la validacion integral del Plan Unico de Cuentas: detecta "
                    + "cuentas huerfanas (ORPHAN), naturaleza incoherente con la clase "
                    + "(WRONG_NATURE), codigos duplicados (DUPLICATE_CODE) y cuentas inactivas "
                    + "con movimientos contables (INACTIVE_WITH_MOVEMENTS). Retorna totales "
                    + "globales y lista detallada de inconsistencias.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reporte de validacion generado correctamente",
                    content = @Content(schema = @Schema(implementation = PucValidationReportDTO.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> validationReport() {
        try {
            PucValidationReportDTO report = chartOfAccountService.validatePuc();
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Reporte de validacion del PUC generado correctamente"),
                            Optional.of(report)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}

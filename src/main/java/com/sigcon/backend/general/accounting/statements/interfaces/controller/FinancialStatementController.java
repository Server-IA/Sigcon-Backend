package com.sigcon.backend.general.accounting.statements.interfaces.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.general.accounting.statements.application.ComparativeRequest;
import com.sigcon.backend.general.accounting.statements.application.StatementRequest;
import com.sigcon.backend.general.accounting.statements.domain.service.FinancialStatementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para los Estados Financieros.
 * Genera Balance General, Estado de Resultados, Flujo de Efectivo
 * y Estados Comparativos conforme a NIIF y normativa colombiana.
 */
@RestController
@RequestMapping("/api/v1/cg/statements")
@RequiredArgsConstructor
@Tag(name = "9. Contabilidad General - Estados Financieros",
     description = "Endpoints para generacion de estados financieros: "
             + "Balance General, Estado de Resultados, Flujo de Efectivo y Comparativos")
@SecurityRequirement(name = "bearerAuth")
public class FinancialStatementController {

    private final FinancialStatementService financialStatementService;

    // ─────────────────────────────────────────────────────
    // Balance General
    // ─────────────────────────────────────────────────────

    @PostMapping("/balance-general")
    @Operation(
            summary = "Balance General",
            description = "Genera el Balance General (Estado de Situacion Financiera) acumulado "
                    + "hasta el periodo indicado. Ecuacion: Activos = Pasivos + Patrimonio."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance General generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> balanceGeneral(@Valid @RequestBody StatementRequest request) {
        try {
            return financialStatementService.getBalanceGeneral(request.getYear(), request.getMonth());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Estado de Resultados
    // ─────────────────────────────────────────────────────

    @PostMapping("/estado-resultados")
    @Operation(
            summary = "Estado de Resultados",
            description = "Genera el Estado de Resultados Integral del periodo indicado. "
                    + "Incluye: Ingresos - Gastos - Costos = Utilidad Neta."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de Resultados generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_FINANCIAL_STATEMENT','TEMP_PERM_VIEW_FINANCIAL_STATEMENT','TEMP_VIEW_FINANCIAL_STATEMENT','PERM_CG.ESTADOS_FINANCIEROS.VER','TEMP_PERM_CG.ESTADOS_FINANCIEROS.VER','TEMP_CG.ESTADOS_FINANCIEROS.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> estadoResultados(@Valid @RequestBody StatementRequest request) {
        try {
            return financialStatementService.getEstadoResultados(request.getYear(), request.getMonth());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Flujo de Efectivo
    // ─────────────────────────────────────────────────────

    @PostMapping("/flujo-efectivo")
    @Operation(
            summary = "Estado de Flujos de Efectivo",
            description = "Genera el Estado de Flujos de Efectivo (NIC 7) para el periodo indicado. "
                    + "Clasifica por actividades: operativa, inversion y financiacion."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flujo de Efectivo generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_FINANCIAL_STATEMENT','TEMP_PERM_VIEW_FINANCIAL_STATEMENT','TEMP_VIEW_FINANCIAL_STATEMENT','PERM_CG.ESTADOS_FINANCIEROS.VER','TEMP_PERM_CG.ESTADOS_FINANCIEROS.VER','TEMP_CG.ESTADOS_FINANCIEROS.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> flujoEfectivo(@Valid @RequestBody StatementRequest request) {
        try {
            return financialStatementService.getFlujoEfectivo(request.getYear(), request.getMonth());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Estado de Cambios en el Patrimonio (HU-CG-18)
    // ─────────────────────────────────────────────────────

    @PostMapping("/cambios-patrimonio")
    @Operation(
            summary = "Estado de Cambios en el Patrimonio",
            description = "Genera el Estado de Cambios en el Patrimonio (HU-CG-18) para el periodo "
                    + "indicado. Cuarto estado financiero obligatorio segun NIC 1. Incluye saldo "
                    + "inicial, aportes, utilidad neta, reservas, resultados acumulados, dividendos "
                    + "y saldo final, con desglose por cuenta de clase 3 del PUC."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de Cambios en el Patrimonio generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_FINANCIAL_STATEMENT','TEMP_PERM_VIEW_FINANCIAL_STATEMENT','TEMP_VIEW_FINANCIAL_STATEMENT','PERM_CG.ESTADOS_FINANCIEROS.VER','TEMP_PERM_CG.ESTADOS_FINANCIEROS.VER','TEMP_CG.ESTADOS_FINANCIEROS.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> cambiosPatrimonio(@Valid @RequestBody StatementRequest request) {
        try {
            return financialStatementService.getEstadoCambiosPatrimonio(request.getYear(), request.getMonth());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Estado Comparativo
    // ─────────────────────────────────────────────────────

    @PostMapping("/comparativo")
    @Operation(
            summary = "Balance General Comparativo",
            description = "Genera un Balance General comparativo entre dos periodos. "
                    + "Calcula variacion absoluta y porcentual por clase contable."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance Comparativo generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodos invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_FINANCIAL_STATEMENT','TEMP_PERM_VIEW_FINANCIAL_STATEMENT','TEMP_VIEW_FINANCIAL_STATEMENT','PERM_CG.ESTADOS_FINANCIEROS.VER','TEMP_PERM_CG.ESTADOS_FINANCIEROS.VER','TEMP_CG.ESTADOS_FINANCIEROS.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> comparativo(@Valid @RequestBody ComparativeRequest request) {
        try {
            return financialStatementService.getComparativo(
                    request.getYear1(), request.getMonth1(),
                    request.getYear2(), request.getMonth2());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

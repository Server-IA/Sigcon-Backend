package com.sigcon.backend.general.accounting.books.interfaces.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.general.accounting.books.domain.service.AccountingBookPdfService;
import com.sigcon.backend.general.accounting.books.domain.service.AccountingBookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para los Libros Contables Oficiales.
 * Genera Libro Diario, Libro Mayor, Balance de Comprobacion
 * y Auxiliares por Cuenta segun normativa contable colombiana.
 */
@RestController
@RequestMapping("/api/v1/cg/books")
@RequiredArgsConstructor
@Tag(name = "9. Contabilidad General - Libros Oficiales",
     description = "Endpoints para generacion de libros contables obligatorios: "
             + "Libro Diario, Libro Mayor, Balance de Comprobacion y Auxiliares por Cuenta")
@SecurityRequirement(name = "bearerAuth")
public class AccountingBookController {

    private final AccountingBookService accountingBookService;
    private final AccountingBookPdfService accountingBookPdfService;

    // ─────────────────────────────────────────────────────
    // Libro Diario
    // ─────────────────────────────────────────────────────

    @GetMapping("/diario")
    @Operation(
            summary = "Libro Diario",
            description = "Genera el Libro Diario del periodo indicado. "
                    + "Muestra todos los asientos contabilizados (POSTED) ordenados por fecha y numero."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Libro Diario generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> libroDiario(
            @Parameter(description = "Anio del periodo contable") @RequestParam Integer year,
            @Parameter(description = "Mes del periodo contable (1-12)") @RequestParam Integer month) {
        return accountingBookService.getLibroDiario(year, month);
    }

    // ─────────────────────────────────────────────────────
    // Libro Mayor
    // ─────────────────────────────────────────────────────

    @GetMapping("/mayor")
    @Operation(
            summary = "Libro Mayor",
            description = "Genera el Libro Mayor del periodo indicado. "
                    + "Agrupa movimientos por cuenta contable con totales de debito, credito y saldo. "
                    + "Si se proporciona accountId, filtra a una sola cuenta."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Libro Mayor generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> libroMayor(
            @Parameter(description = "Anio del periodo contable") @RequestParam Integer year,
            @Parameter(description = "Mes del periodo contable (1-12)") @RequestParam Integer month,
            @Parameter(description = "ID de cuenta contable (opcional, null = todas)") @RequestParam(required = false) Long accountId) {
        return accountingBookService.getLibroMayor(year, month, accountId);
    }

    // ─────────────────────────────────────────────────────
    // Balance de Comprobacion
    // ─────────────────────────────────────────────────────

    @GetMapping("/balance-comprobacion")
    @Operation(
            summary = "Balance de Comprobacion",
            description = "Genera el Balance de Comprobacion del periodo indicado. "
                    + "Incluye saldo anterior, movimientos del periodo y saldo final por cuenta."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance de Comprobacion generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> balanceComprobacion(
            @Parameter(description = "Anio del periodo contable") @RequestParam Integer year,
            @Parameter(description = "Mes del periodo contable (1-12)") @RequestParam Integer month) {
        return accountingBookService.getBalanceComprobacion(year, month);
    }

    // ─────────────────────────────────────────────────────
    // Auxiliares por Cuenta
    // ─────────────────────────────────────────────────────

    @GetMapping("/auxiliares")
    @Operation(
            summary = "Auxiliar por Cuenta",
            description = "Genera el Auxiliar de una cuenta contable para el periodo indicado. "
                    + "Muestra cada movimiento con saldo acumulado progresivo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Auxiliar generado correctamente"),
            @ApiResponse(responseCode = "400", description = "accountId es obligatorio o parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> auxiliaresCuentas(
            @Parameter(description = "Anio del periodo contable") @RequestParam Integer year,
            @Parameter(description = "Mes del periodo contable (1-12)") @RequestParam Integer month,
            @Parameter(description = "ID de la cuenta contable") @RequestParam Long accountId) {
        return accountingBookService.getAuxiliaresCuentas(year, month, accountId);
    }

    // ─────────────────────────────────────────────────────
    // PDFs de libros oficiales (HU-CG-22, HU-CG-25, HU-CG-26)
    // ─────────────────────────────────────────────────────

    /**
     * Genera el PDF del Libro Diario del periodo (HU-CG-22).
     *
     * @param year  anio del periodo
     * @param month mes del periodo (1-12)
     * @return PDF en stream inline listo para visualizar o descargar
     */
    @GetMapping("/diario/pdf")
    @Operation(
            summary = "Libro Diario en PDF",
            description = "Genera el Libro Diario del periodo en formato PDF (HU-CG-22). "
                    + "Incluye encabezado con datos de la empresa, tabla de asientos con lineas "
                    + "y totales de cuadratura contable."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> libroDiarioPdf(
            @Parameter(description = "Anio del periodo contable") @RequestParam Integer year,
            @Parameter(description = "Mes del periodo contable (1-12)") @RequestParam Integer month) {
        byte[] pdf = accountingBookPdfService.generateLibroDiarioPdf(year, month);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        "inline; filename=LibroDiario_" + year + "_" + String.format("%02d", month) + ".pdf")
                .body(pdf);
    }

    /**
     * Genera el PDF del Libro Mayor del periodo (HU-CG-22).
     * Si accountId es null, lista todas las cuentas; si se indica, solo esa cuenta.
     *
     * @param year      anio del periodo
     * @param month     mes del periodo (1-12)
     * @param accountId identificador de cuenta (opcional)
     * @return PDF en stream inline
     */
    @GetMapping("/mayor/pdf")
    @Operation(
            summary = "Libro Mayor en PDF",
            description = "Genera el Libro Mayor del periodo en formato PDF (HU-CG-22). "
                    + "Si se proporciona accountId, filtra a una sola cuenta; si no, lista todas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> libroMayorPdf(
            @Parameter(description = "Anio del periodo contable") @RequestParam Integer year,
            @Parameter(description = "Mes del periodo contable (1-12)") @RequestParam Integer month,
            @Parameter(description = "ID de cuenta contable (opcional)") @RequestParam(required = false) Long accountId) {
        byte[] pdf = accountingBookPdfService.generateLibroMayorPdf(year, month, accountId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        "inline; filename=LibroMayor_" + year + "_" + String.format("%02d", month) + ".pdf")
                .body(pdf);
    }

    /**
     * Genera el PDF del Balance de Comprobacion (HU-CG-25).
     *
     * @param year  anio del periodo
     * @param month mes del periodo (1-12)
     * @return PDF en stream inline
     */
    @GetMapping("/balance-comprobacion/pdf")
    @Operation(
            summary = "Balance de Comprobacion en PDF",
            description = "Genera el Balance de Comprobacion del periodo en formato PDF (HU-CG-25). "
                    + "Incluye saldo inicial, movimientos y saldo final por cuenta con totales de cuadratura."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generado correctamente"),
            @ApiResponse(responseCode = "400", description = "Parametros de periodo invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> balanceComprobacionPdf(
            @Parameter(description = "Anio del periodo contable") @RequestParam Integer year,
            @Parameter(description = "Mes del periodo contable (1-12)") @RequestParam Integer month) {
        byte[] pdf = accountingBookPdfService.generateBalanceComprobacionPdf(year, month);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        "inline; filename=BalanceComprobacion_" + year + "_" + String.format("%02d", month) + ".pdf")
                .body(pdf);
    }

    /**
     * Genera el PDF del Auxiliar por Cuenta para el periodo (HU-CG-26).
     *
     * @param year      anio del periodo
     * @param month     mes del periodo (1-12)
     * @param accountId identificador de la cuenta contable (obligatorio)
     * @return PDF en stream inline
     */
    @GetMapping("/auxiliares/pdf")
    @Operation(
            summary = "Auxiliar por Cuenta en PDF",
            description = "Genera el Auxiliar por Cuenta del periodo en formato PDF (HU-CG-26). "
                    + "Detalle de cada movimiento con saldo acumulado progresivo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generado correctamente"),
            @ApiResponse(responseCode = "400", description = "accountId obligatorio o parametros invalidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> auxiliaresCuentasPdf(
            @Parameter(description = "Anio del periodo contable") @RequestParam Integer year,
            @Parameter(description = "Mes del periodo contable (1-12)") @RequestParam Integer month,
            @Parameter(description = "ID de la cuenta contable") @RequestParam Long accountId) {
        byte[] pdf = accountingBookPdfService.generateAuxiliaresPdf(year, month, accountId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        "inline; filename=Auxiliar_" + accountId + "_" + year + "_"
                                + String.format("%02d", month) + ".pdf")
                .body(pdf);
    }
}

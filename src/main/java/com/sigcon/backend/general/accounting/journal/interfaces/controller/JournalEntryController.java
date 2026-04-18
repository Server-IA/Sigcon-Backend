package com.sigcon.backend.general.accounting.journal.interfaces.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.application.ReverseEntryRequest;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para la gestion de asientos contables.
 * Base path: /api/v1/journal-entries
 */
@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
@Tag(name = "Asientos Contables", description = "Endpoints para gestion del motor central de asientos contables")
@SecurityRequirement(name = "bearerAuth")
public class JournalEntryController {

    private final JournalEntryService journalEntryService;

    // ─────────────────────────────────────────────────────
    // Busqueda paginada DataTable
    // ─────────────────────────────────────────────────────

    @PostMapping("/search")
    @Operation(
            summary = "Buscar asientos contables",
            description = "Busqueda paginada de asientos contables compatible con DataTable. "
                    + "Soporta filtros globales y por columna, ordenamiento y paginacion."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Error en los parametros de busqueda"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> search(@RequestBody DataTableRequest request) {
        try {
            return journalEntryService.searchEntries(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Crear asiento contable (DRAFT)
    // ─────────────────────────────────────────────────────

    @PostMapping("/store")
    @Operation(
            summary = "Crear asiento contable",
            description = "Crea un nuevo asiento contable en estado BORRADOR. "
                    + "Valida partida doble, periodo abierto y cuentas activas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asiento creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion (partida doble, periodo cerrado, cuenta inactiva)"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> store(
            @Valid @RequestBody CreateJournalEntryRequest request,
            Authentication authentication) {
        try {
            String createdBy = authentication != null ? authentication.getName() : "SYSTEM";
            JournalEntryDTO result = journalEntryService.createEntry(request, createdBy);
            return ResponseEntity.ok(Map.of(
                    "message", "Asiento contable creado correctamente.",
                    "data", result
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Obtener detalle de asiento
    // ─────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(
            summary = "Detalle de asiento contable",
            description = "Retorna la informacion completa de un asiento con sus lineas de detalle."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente"),
            @ApiResponse(responseCode = "404", description = "Asiento no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            JournalEntryDTO result = journalEntryService.getEntry(id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Contabilizar asiento: DRAFT -> POSTED
    // ─────────────────────────────────────────────────────

    @PostMapping("/{id}/post")
    @Operation(
            summary = "Contabilizar asiento",
            description = "Cambia el estado de un asiento de BORRADOR a CONTABILIZADO."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asiento contabilizado correctamente"),
            @ApiResponse(responseCode = "400", description = "El asiento no esta en estado BORRADOR"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> post(@PathVariable Long id) {
        try {
            JournalEntryDTO result = journalEntryService.postEntry(id);
            return ResponseEntity.ok(Map.of(
                    "message", "Asiento contabilizado correctamente.",
                    "data", result
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Reversar asiento contabilizado
    // ─────────────────────────────────────────────────────

    @PostMapping("/{id}/reverse")
    @Operation(
            summary = "Reversar asiento contable",
            description = "Crea un asiento espejo con debitos y creditos invertidos. "
                    + "El asiento original queda en estado REVERSADO."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asiento reversado correctamente"),
            @ApiResponse(responseCode = "400", description = "El asiento no esta en estado CONTABILIZADO o el periodo esta cerrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> reverse(
            @PathVariable Long id,
            @Valid @RequestBody ReverseEntryRequest request,
            Authentication authentication) {
        try {
            String createdBy = authentication != null ? authentication.getName() : "SYSTEM";
            JournalEntryDTO result = journalEntryService.reverseEntry(id, request.getDescription(), createdBy);
            return ResponseEntity.ok(Map.of(
                    "message", "Asiento reversado correctamente.",
                    "data", result
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // CG-07A: Actualizar asiento BORRADOR
    // ─────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar asiento contable en BORRADOR",
            description = "CG-07A: Modifica un asiento en estado DRAFT. Revalida partida doble, "
                        + "cuentas activas y periodo abierto. Asientos CONTABILIZADOS no se pueden "
                        + "modificar — use /correct para crear una version correctiva."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asiento actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Asiento no esta en DRAFT, partida doble desbalanceada, o periodo cerrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id,
                                     @Valid @RequestBody CreateJournalEntryRequest request) {
        try {
            JournalEntryDTO updated = journalEntryService.updateEntry(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // CG-07B: Crear asiento correctivo (version)
    // ─────────────────────────────────────────────────────

    @PostMapping("/{id}/correct")
    @Operation(
            summary = "Crear correccion (version) de un asiento CONTABILIZADO",
            description = "CG-07B: Crea un nuevo asiento en DRAFT con correctionOf apuntando al original. "
                        + "El original permanece inmutable (principio contable). Post la correccion "
                        + "con /post tras revision."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Correccion creada en BORRADOR"),
            @ApiResponse(responseCode = "400", description = "Asiento original no esta CONTABILIZADO o validacion fallida"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> correct(@PathVariable Long id,
                                      @Valid @RequestBody CreateJournalEntryRequest request) {
        try {
            String createdBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : "sistema";
            JournalEntryDTO correction = journalEntryService.createCorrection(id, request, createdBy);
            return ResponseEntity.ok(correction);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Eliminar asiento (solo DRAFT)
    // ─────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Eliminar asiento contable",
            description = "Eliminacion logica de un asiento. Solo se permite para asientos en estado BORRADOR."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asiento eliminado correctamente"),
            @ApiResponse(responseCode = "400", description = "El asiento no esta en estado BORRADOR"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            journalEntryService.deleteEntry(id);
            return ResponseEntity.ok(Map.of("message", "Asiento eliminado correctamente."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Consultar asientos por periodo
    // ─────────────────────────────────────────────────────

    @GetMapping("/period/{year}/{month}")
    @Operation(
            summary = "Asientos por periodo",
            description = "Retorna todos los asientos contables de un periodo especifico (anio-mes)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> byPeriod(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        List<JournalEntryDTO> results = journalEntryService.getEntriesByPeriod(year, month);
        return ResponseEntity.ok(results);
    }
}

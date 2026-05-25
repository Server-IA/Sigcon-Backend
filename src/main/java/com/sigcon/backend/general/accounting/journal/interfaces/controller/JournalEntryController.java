package com.sigcon.backend.general.accounting.journal.interfaces.controller;

import java.util.List;
import java.util.HashMap;
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

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.application.ReverseEntryRequest;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryExportService;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.utils.DataTableRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
    private final JournalEntryExportService journalEntryExportService;
    private final AuditPublisher auditPublisher;
    private final com.sigcon.backend.audit.domain.service.AuditLogService auditLogService;

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
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> search(@RequestBody DataTableRequest request) {
        try {
            return journalEntryService.searchEntries(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
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
    // HU-CG-03A E1: el rol CONTADOR debe poder crear comprobantes contables.
    // Antes solo aceptaba ROLE_ADMIN -> 403 al CONTADOR. V9-ZZM agrega
    // PERM_CREATE_JOURNAL_ENTRY al rol CONTADOR.
    @PreAuthorize("hasAuthority('PERM_CG.COMPROBANTES.CREAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
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
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            JournalEntryDTO result = journalEntryService.getEntry(id);
            // HU-CG-08A E4: registrar evento VIEW cuando se consulta un asiento
            // REVERSED para trazabilidad de auditores. Severidad MEDIUM porque la
            // consulta de asientos anulados es un evento sensible para reportes
            // forenses contables.
            if (result != null && "REVERSED".equalsIgnoreCase(String.valueOf(result.getStatus()))) {
                auditPublisher.publish(
                        AuditAction.VIEW, AuditModule.CG, AuditSeverity.MEDIUM,
                        "JournalEntry", id,
                        "Consulta de comprobante REVERSED #" + id +
                        (result.getReversalOfNumber() != null ? " (reversa de " + result.getReversalOfNumber() + ")" : ""),
                        null, null, id);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
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
    // HU-CG-02B: contabilizar requiere permiso APPROVE (no solo VIEW).
    // Segregacion de funciones: el operador VIEW solo lee, contabilizar es accion.
    @PreAuthorize("hasAuthority('PERM_CG.COMPROBANTES.CONTABILIZAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> post(@PathVariable Long id) {
        try {
            JournalEntryDTO result = journalEntryService.postEntry(id);
            return ResponseEntity.ok(Map.of(
                    "message", "Asiento contabilizado correctamente.",
                    "data", result
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
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
    // HU-CG-07B: reversar requiere permiso REVERSE explicito.
    @PreAuthorize("hasAuthority('PERM_CG.COMPROBANTES.REVERSAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> reverse(
            @PathVariable Long id,
            @Valid @RequestBody ReverseEntryRequest request,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        try {
            String createdBy = authentication != null ? authentication.getName() : "SYSTEM";
            boolean withDraft = Boolean.TRUE.equals(request.getCreateCorrectionDraft());
            JournalEntryDTO result = journalEntryService.reverseEntry(
                    id, request.getDescription(), createdBy, withDraft);
            // HU-CG-08B E6: bitacora enriquecida con usuario+rol+IP+motivo+JE original/reversa
            String roles = authentication != null && authentication.getAuthorities() != null
                    ? authentication.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .filter(s -> s.startsWith("ROLE_") || s.equals("PLATFORM_ADMIN"))
                        .reduce((a, b) -> a + "," + b).orElse("-")
                    : "-";
            String fwd = httpRequest != null ? httpRequest.getHeader("X-Forwarded-For") : null;
            String ip = (fwd != null && !fwd.isBlank())
                    ? fwd.split(",")[0].trim()
                    : (httpRequest != null ? httpRequest.getRemoteAddr() : "-");
            String ua = httpRequest != null ? httpRequest.getHeader("User-Agent") : "-";
            String desc = String.format(
                "Reverse JE original=%s reversa=%s | usuario=%s | roles=%s | IP=%s | UA=%s | motivo=%s",
                id, result != null ? result.getId() : "?", createdBy, roles, ip,
                ua != null && ua.length() > 80 ? ua.substring(0, 80) + "..." : ua,
                request.getDescription());
            auditPublisher.publish(
                    AuditAction.UPDATE, AuditModule.CG, AuditSeverity.HIGH,
                    "JournalEntry", id, desc, null, null, id);
            return ResponseEntity.ok(Map.of(
                    "message", "Asiento reversado correctamente.",
                    "data", result
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
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
    // HU-CG-07A: editar borrador requiere permiso UPDATE.
    @PreAuthorize("hasAuthority('PERM_CG.COMPROBANTES.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id,
                                     @Valid @RequestBody CreateJournalEntryRequest request) {
        try {
            JournalEntryDTO updated = journalEntryService.updateEntry(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
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
    // HU-CG-07B: crear correccion sobre POSTED requiere permiso UPDATE
    // (genera un DRAFT nuevo, no toca el original).
    @PreAuthorize("hasAuthority('PERM_CG.COMPROBANTES.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> correct(@PathVariable Long id,
                                      @Valid @RequestBody CreateJournalEntryRequest request) {
        try {
            String createdBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : "sistema";
            JournalEntryDTO correction = journalEntryService.createCorrection(id, request, createdBy);
            return ResponseEntity.ok(correction);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
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
    // Eliminar (soft) requiere permiso DELETE explicito.
    @PreAuthorize("hasAuthority('PERM_CG.COMPROBANTES.ELIMINAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            journalEntryService.deleteEntry(id);
            return ResponseEntity.ok(Map.of("message", "Asiento eliminado correctamente."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Documentos relacionados (HU-CG-08C E2)
    // ─────────────────────────────────────────────────────

    /**
     * HU-CG-07C E1/E2/E3: arbol completo de versiones del comprobante.
     * Recorre RECURSIVAMENTE las relaciones reversalOf/correctionOf desde la raiz
     * y devuelve un grafo plano con todas las versiones (originales + correcciones
     * + reversiones) vinculadas. Cada nodo trae parentId + depth para que el
     * frontend renderice un arbol jerarquico.
     */
    @GetMapping("/{id}/versions")
    @Operation(
            summary = "Historial completo de versiones del comprobante",
            description = "HU-CG-07C: Arbol jerarquico de TODAS las versiones (originales, "
                        + "correcciones y reversiones) vinculadas al comprobante. Util para "
                        + "auditoria forense contable. Incluye parentId + depth por nodo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de versiones obtenido"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> versionHistory(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of(
                    "data", journalEntryService.getVersionHistory(id)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
        }
    }

    /**
     * HU-CG-07C E3 (QA 2026-05-25): comparacion detallada entre DOS versiones.
     * Devuelve el diff de cabecera (campo, valor anterior/nuevo) y de lineas
     * (agregadas/eliminadas/modificadas) para que el auditor identifique los
     * cambios especificos entre dos versiones del comprobante.
     */
    @GetMapping("/{idA}/versions/compare/{idB}")
    @Operation(
            summary = "Comparar dos versiones de un comprobante (HU-CG-07C E3)",
            description = "Retorna las diferencias de cabecera y de lineas entre dos versiones "
                        + "del comprobante, resaltando campos modificados, valores anteriores y nuevos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comparacion generada"),
            @ApiResponse(responseCode = "400", description = "Alguna version no existe")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> compareVersions(@PathVariable Long idA, @PathVariable Long idB) {
        try {
            return ResponseEntity.ok(Map.of("data", journalEntryService.compareVersions(idA, idB)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
        }
    }

    /**
     * HU-AU-09 E5 (2026-04-28): consulta inversa via FK bidireccional.
     * Retorna el log de auditoria principal del JE + todos los logs vinculados
     * (los que tengan journal_entry_id apuntando a este id).
     */
    @GetMapping("/{id}/audit-trail")
    @Operation(
            summary = "Trazabilidad de auditoria del comprobante (HU-AU-09 E5)",
            description = "Retorna el log principal de creacion (JE.audit_log_id) + "
                        + "todos los logs vinculados a este JE (journal_entry_id). "
                        + "Confirma vinculacion bidireccional para cumplimiento normativo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trazabilidad obtenida"),
            @ApiResponse(responseCode = "400", description = "Asiento no encontrado")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> auditTrail(@PathVariable Long id) {
        try {
            var je = journalEntryService.getEntry(id);
            Map<String, Object> response = new HashMap<>();
            response.put("journalEntryId", id);
            response.put("auditLogId", je.getAuditLogId());
            response.put("voucherCode", je.getVoucherCode());
            response.put("status", je.getStatus());
            // Logs adicionales: por journalEntryId
            response.put("relatedAuditLogs", auditLogService.findByJournalEntry(id));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage() != null ? e.getMessage() : "Asiento contable no encontrado",
                    "message", e.getMessage() != null ? e.getMessage() : "Asiento contable no encontrado",
                    "msg", e.getMessage() != null ? e.getMessage() : "Asiento contable no encontrado"));
        }
    }

    @GetMapping("/{id}/related-docs")
    @Operation(
            summary = "Documentos relacionados al comprobante",
            description = "HU-CG-08C E2/E3: devuelve los vinculos cruzados visibles desde el "
                        + "comprobante consultado: si es una reversion, el comprobante original; "
                        + "si fue reversado, el comprobante REV-XXXX que lo neutralizo; "
                        + "si fue corregido, el comprobante COR-XXXX vinculado; "
                        + "y el modulo origen (AP/AR/BNK/ACT/NOM) cuando aplique."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de documentos relacionados"),
            @ApiResponse(responseCode = "404", description = "Comprobante no encontrado")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> relatedDocs(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of(
                    "data", journalEntryService.getRelatedDocuments(id)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────
    // Exportar comprobante (HU-CG-01C)
    // ─────────────────────────────────────────────────────

    @GetMapping("/{id}/pdf")
    @Operation(
            summary = "Exportar comprobante a PDF",
            description = "HU-CG-01C E1/E3: descarga el comprobante (en cualquier estado) como PDF "
                        + "con cabecera de empresa, datos del asiento y detalle de lineas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generado"),
            @ApiResponse(responseCode = "400", description = "Comprobante no encontrado o error de generacion"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> exportPdf(@PathVariable Long id) {
        try {
            byte[] body = journalEntryExportService.generatePdf(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"comprobante-" + id + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
        }
    }

    /**
     * HU-CG-02B E3 (QA 2026-05-19): exportacion masiva del listado de
     * comprobantes filtrados. Recibe en el body el mismo DataTableRequest
     * del listado y devuelve CSV o XLSX con todos los registros filtrados.
     */
    @PostMapping("/export/{format}")
    @Operation(summary = "Exportar listado completo de comprobantes",
            description = "HU-CG-02B E3: descarga la lista completa de comprobantes que "
                    + "cumplen los filtros del DataTable en formato CSV o XLSX. PDF por "
                    + "comprobante individual usa /{id}/pdf.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo generado"),
            @ApiResponse(responseCode = "400", description = "Formato no soportado")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> exportListing(@org.springframework.web.bind.annotation.PathVariable String format,
                                            @RequestBody DataTableRequest request) {
        try {
            var entries = journalEntryService.findFilteredAsList(request);
            JournalEntryExportService.ListExportResult result =
                    journalEntryExportService.exportListing(entries, format);
            // Audit EXPORT lista
            try {
                auditPublisher.publish(AuditAction.EXPORT, AuditModule.CG,
                        AuditSeverity.LOW, "JournalEntry", null,
                        "Export listado de comprobantes formato=" + format
                                + " filas=" + entries.size(), null, null, null);
            } catch (RuntimeException ignored) { /* audit no rompe */ }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + result.fileName + "\"")
                    .contentType(MediaType.parseMediaType(result.mime))
                    .body(result.content);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(),
                    "message", e.getMessage(), "msg", e.getMessage()));
        }
    }

    @GetMapping("/{id}/xlsx")
    @Operation(
            summary = "Exportar comprobante a Excel",
            description = "HU-CG-01C E2: descarga el comprobante como hoja de calculo Excel (.xlsx)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XLSX generado"),
            @ApiResponse(responseCode = "400", description = "Comprobante no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> exportXlsx(@PathVariable Long id) {
        try {
            byte[] body = journalEntryExportService.generateXlsx(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"comprobante-" + id + ".xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "message", e.getMessage(), "msg", e.getMessage()));
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
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> byPeriod(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        List<JournalEntryDTO> results = journalEntryService.getEntriesByPeriod(year, month);
        return ResponseEntity.ok(results);
    }
}

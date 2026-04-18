package com.sigcon.backend.accounts_receivable.credit_debit_notes.interfaces.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.accounts_receivable.credit_debit_notes.application.CreateArNoteRequest;
import com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.service.ArNoteService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de notas credito y debito sobre facturas de venta.
 * Cubre HU AR-07.
 * Provee endpoints para crear notas, consultarlas por identificador y listarlas por factura.
 */
@RestController
@RequestMapping("/api/v1/ar/notes")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Operaciones",
     description = "Endpoints para registro y consulta de cobros, anticipos y notas de facturas de venta")
public class ArNoteController {

    private final ArNoteService noteService;

    /**
     * Crea una nueva nota credito o debito asociada a una factura de venta.
     *
     * @param request       datos de la nota
     * @param bindingResult resultado de validacion
     * @return nota creada o errores de validacion
     */
    @Operation(summary = "Crear nota credito/debito",
               description = "Crea una nota credito (reduce saldo, Debito Ingresos / Credito CxC) o nota debito (incrementa saldo, Debito CxC / Credito Ingresos) sobre una factura de venta. Genera asiento contable automaticamente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_CREATE_AR_NOTE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createNote(@Valid @RequestBody CreateArNoteRequest request,
                                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            return noteService.createNote(request);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Consulta notas credito/debito con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de notas
     */
    @Operation(summary = "Buscar notas", description = "Lista notas credito y debito con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de notas")
    })
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_READ_AR_NOTE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> searchNotes(@RequestBody(required = false) DataTableRequest request) {
        return noteService.getNotes(request);
    }

    /**
     * Obtiene una nota por su identificador.
     *
     * @param id identificador de la nota
     * @return nota encontrada
     */
    @Operation(summary = "Obtener nota por id", description = "Obtiene una nota credito o debito por su identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota encontrada"),
            @ApiResponse(responseCode = "400", description = "Nota no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_READ_AR_NOTE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return noteService.getById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Obtiene todas las notas asociadas a una factura de venta.
     *
     * @param invoiceId identificador de la factura
     * @return lista de notas de la factura
     */
    @Operation(summary = "Notas por factura", description = "Obtiene todas las notas credito y debito de una factura de venta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de notas de la factura")
    })
    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasAuthority('PERM_READ_AR_NOTE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getNotesByInvoice(@PathVariable Long invoiceId) {
        return noteService.getNotesByInvoice(invoiceId);
    }
}

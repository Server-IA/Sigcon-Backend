package com.sigcon.backend.invoices.ap_notes.interfaces.controller;

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

import com.sigcon.backend.invoices.ap_notes.application.CreateApNoteRequest;
import com.sigcon.backend.invoices.ap_notes.domain.service.ApNoteService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gestion de notas credito y debito de facturas de compra.
 * Provee endpoints para crear notas, consultarlas y listarlas por factura.
 */
@RestController
@RequestMapping("/api/v1/ap/notes")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Notas Credito/Debito", description = "Endpoints para gestion de notas credito y debito de facturas de compra")
public class ApNoteController {

    private final ApNoteService noteService;

    /**
     * Consulta notas credito/debito con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de notas
     */
    @Operation(summary = "Consultar notas", description = "Lista notas credito y debito con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de notas")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_READ_AP_NOTE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> searchNotes(@RequestBody(required = false) DataTableRequest request) {
        return noteService.getNotes(request);
    }

    /**
     * Crea una nueva nota credito o debito asociada a una factura de compra.
     *
     * @param request       datos de la nota
     * @param bindingResult resultado de validacion
     * @return nota creada o errores de validacion
     */
    @Operation(summary = "Crear nota credito/debito", description = "Crea una nota credito (reduce saldo) o debito (incrementa saldo) para una factura. Genera asiento contable automaticamente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_AP_NOTE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createNote(@Valid @RequestBody CreateApNoteRequest request,
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
     * Obtiene todas las notas asociadas a una factura especifica.
     *
     * @param invoiceId identificador de la factura
     * @return lista de notas de la factura
     */
    @Operation(summary = "Notas por factura", description = "Obtiene todas las notas credito y debito registradas para una factura especifica")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de notas de la factura")
    })
    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasAuthority('PERM_READ_AP_NOTE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getNotesByInvoice(@PathVariable Long invoiceId) {
        return noteService.getNotesByInvoice(invoiceId);
    }
}

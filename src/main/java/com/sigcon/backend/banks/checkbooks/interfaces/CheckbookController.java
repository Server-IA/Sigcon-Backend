package com.sigcon.backend.banks.checkbooks.interfaces;

import com.sigcon.backend.banks.checkbooks.application.CheckbookDeleteRequest;
import com.sigcon.backend.banks.checkbooks.application.CheckbookRequest;
import com.sigcon.backend.banks.checkbooks.domain.service.CheckbookService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/banks/checkbooks")
@RequiredArgsConstructor
@Tag(name = "5. Módulo de Bancos - Chequeras", description = "Endpoints para gestión de chequeras")
@SecurityRequirement(name = "bearerAuth")
public class CheckbookController {

    private final CheckbookService service;

    // =========================
    // CREATE
    // =========================
    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CREATE_CHECKBOOK') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Crear chequera")
    public ResponseEntity<?> create(@Valid @RequestBody CheckbookRequest request) {
        return ResponseEntity.ok(service.save(request, null));
    }

    // =========================
    // UPDATE
    // =========================
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_CHECKBOOK') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Actualizar chequera")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody CheckbookRequest request) {
        return ResponseEntity.ok(service.save(request, id));
    }

    // =========================
    // DELETE (INACTIVATE)
    // =========================
    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('PERM_DELETE_CHECKBOOK') or hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Anular/Bloquear chequera")
    public ResponseEntity<?> delete(@RequestBody CheckbookDeleteRequest request) {
        return ResponseEntity.ok(service.delete(request));
    }

    // =========================
    // SEARCH (CLAVE 🔥)
    // =========================
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_CHECKBOOK') or hasAuthority('ROLE_ADMIN')")
    @Operation(
        summary = "Consultar chequeras",
        description = "Consulta paginada tipo DataTable",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = "{\n" +
                            "  \"draw\": 1,\n" +
                            "  \"start\": 0,\n" +
                            "  \"length\": 20,\n" +
                            "  \"search\": {\n" +
                            "    \"value\": \"1001\",\n" +
                            "    \"regex\": true\n" +
                            "  },\n" +
                            "  \"columns\": [],\n" +
                            "  \"order\": []\n" +
                            "}"
                )
            )
        )
    )
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        return service.search(request);
    }

}
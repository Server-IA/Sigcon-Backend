package com.sigcon.backend.checkbooks.interfaces;

import com.sigcon.backend.checkbooks.application.*;
import com.sigcon.backend.checkbooks.domain.service.CheckbookService;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/checkbooks")
@RequiredArgsConstructor
@Tag(name = "Checkbooks", description = "Gestión de chequeras")
public class CheckbookController {

    private final CheckbookService service;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CREATE_CHEQUERAS') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Crear chequera")
    public ResponseEntity<?> create(@RequestBody CheckbookRequest request) {

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Chequera creada correctamente"),
                        Optional.of(service.save(request, null))
                )
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar chequera")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody CheckbookRequest request) {

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Chequera actualizada correctamente"),
                        Optional.of(service.save(request, id))
                )
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar / Inactivar chequera")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                   @RequestBody CheckbookDeleteRequest request) {

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Operación realizada correctamente"),
                        Optional.of(service.deleteOrDisable(id, request.getMotivo()))
                )
        );
    }

    @PostMapping("/search")
    @Operation(summary = "Consultar chequeras")
    public ResponseEntity<?> search() {

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Consulta realizada correctamente"),
                        Optional.of(service.findAll())
                )
        );
    }
}

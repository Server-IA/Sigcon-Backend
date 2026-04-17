package com.sigcon.backend.nomina.interfaces.controller;

import com.sigcon.backend.nomina.application.CreatePayrollConceptRequest;
import com.sigcon.backend.nomina.domain.service.PayrollConceptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HU-NOM-02: REST de conceptos de nomina.
 *
 * <p>Los 17 conceptos legales colombianos estan precargados en la migracion
 * V9-G. El admin puede agregar conceptos adicionales o cambiar porcentajes
 * de los existentes.
 */
@RestController
@RequestMapping("/api/nomina/conceptos")
@RequiredArgsConstructor
@Tag(name = "Nomina - Conceptos",
     description = "Catalogo de conceptos con formula + cuentas PUC (HU-NOM-02)")
public class PayrollConceptController {

    private final PayrollConceptService service;

    @Operation(summary = "Listar conceptos (opcional filtro por tipo/estado)",
            description = "Sin filtros devuelve todos. Acepta status (ACTIVE/INACTIVE) y "
                    + "type (EARNING/DEDUCTION/EMPLOYER_CONTRIBUTION).")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Listado de conceptos"))
    @GetMapping
    public ResponseEntity<?> list(
            @Parameter(description = "Estado", example = "ACTIVE")
            @RequestParam(required = false) String status,
            @Parameter(description = "Tipo", example = "DEDUCTION")
            @RequestParam(required = false) String type) {
        return service.list(status, type);
    }

    @Operation(summary = "Obtener concepto por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Concepto encontrado"),
            @ApiResponse(responseCode = "400", description = "Concepto no existe")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            @Parameter(description = "ID del concepto", example = "1") @PathVariable Long id) {
        return service.getById(id);
    }

    @Operation(summary = "Crear concepto de nomina (HU-NOM-02)",
            description = "Valida unicidad de code + que las cuentas PUC debito/credito esten "
                    + "ACTIVE (HU-NOM-02 E3). Si alguna cuenta esta INACTIVE, rechaza con "
                    + "mensaje exacto del Excel.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Concepto creado"),
            @ApiResponse(responseCode = "400", description = "Cuenta PUC inactiva, code duplicado o datos invalidos")
    })
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreatePayrollConceptRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Actualizar concepto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Concepto actualizado"),
            @ApiResponse(responseCode = "400", description = "Cuenta PUC inactiva o concepto inexistente")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "ID del concepto", example = "1") @PathVariable Long id,
            @Valid @RequestBody CreatePayrollConceptRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Eliminar concepto (soft delete)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Concepto eliminado"),
            @ApiResponse(responseCode = "400", description = "Concepto no existe")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "ID del concepto", example = "1") @PathVariable Long id) {
        return service.delete(id);
    }
}

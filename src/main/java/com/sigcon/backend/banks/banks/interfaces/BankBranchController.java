package com.sigcon.backend.banks.banks.interfaces;

import com.sigcon.backend.banks.banks.application.BankBranchDTO;
import com.sigcon.backend.banks.banks.domain.service.BankBranchService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bank-branches")
@RequiredArgsConstructor
@Tag(name = "5. Módulo de Bancos - Sucursales Bancarias", description = "Endpoints para gestión de sucursales bancarias")
@SecurityRequirement(name = "bearerAuth")
public class BankBranchController {

    private final BankBranchService bankBranchService;
    // fddf

    @PostMapping("/store")
    @Operation(summary = "Registrar sucursal", description = "Crea una sucursal bancaria asociada a un banco.<br>Permiso requerido: CREATE_BANK_BRANCH", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Payload de creación de la sucursal", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BankBranchDTO.class), examples = @ExampleObject(value = """
            {
              "address": "Calle 50 # 10-20",
              "city": "Bogotá",
              "mainBranch": false,
              "bankId": 1
            }
            """))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal registrada correctamente"),
            @ApiResponse(responseCode = "400", description = "Error de validación"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_CREATE_BANK_BRANCH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> store(
            @Valid @RequestBody(required = false) BankBranchDTO request,
            BindingResult bindingResult) {

        return bankBranchService.create(request, bindingResult);
    }

    @PostMapping("/search")
    @Operation(summary = "Consultar sucursales", description = "Consulta paginada de sucursales con filtros (DataTable).<br>Permiso requerido: VIEW_BANK_BRANCH", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, description = "Objeto DataTableRequest con filtros y paginación.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DataTableRequest.class), examples = @ExampleObject(value = """
            {
              "draw": 1,
              "start": 0,
              "length": 10,
              "search": { "value": "Bogotá", "regex": false },
              "columns": [],
              "order": []
            }
            """))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos"),
            @ApiResponse(responseCode = "404", description = "Sin resultados")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_BANK_BRANCH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {

        return bankBranchService.findAllPaged(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de sucursal", description = "Retorna la información detallada de una sucursal por ID.<br>Permiso requerido: VIEW_BANK_BRANCH")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente"),
            @ApiResponse(responseCode = "404", description = "Sucursal no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_BANK_BRANCH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> detail(@PathVariable Long id) {

        return bankBranchService.getDetail(id);
    }

    @GetMapping("/bank/{bankId}")
    @Operation(summary = "Sucursales por banco", description = "Retorna todas las sucursales activas asociadas a un banco específico.<br>Permiso requerido: VIEW_BANK_BRANCH")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursales obtenidas correctamente"),
            @ApiResponse(responseCode = "404", description = "Banco no encontrado o sin sucursales"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_BANK_BRANCH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> findByBank(@PathVariable Long bankId) {

        return bankBranchService.findByBank(bankId);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar sucursal", description = "Actualiza la información de una sucursal existente.<br>Permiso requerido: UPDATE_BANK_BRANCH", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = BankBranchDTO.class), examples = @ExampleObject(value = """
            {
              "address": "Carrera 7 # 32-15",
              "city": "Medellín",
              "mainBranch": true
            }
            """))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal actualizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Error de validación"),
            @ApiResponse(responseCode = "404", description = "Sucursal no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_UPDATE_BANK_BRANCH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody BankBranchDTO request,
            BindingResult bindingResult) {

        return bankBranchService.update(id, request, bindingResult);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar sucursal", description = "Eliminación lógica de la sucursal.<br>Permiso requerido: DELETE_BANK_BRANCH")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal eliminada correctamente"),
            @ApiResponse(responseCode = "404", description = "Sucursal no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_DELETE_BANK_BRANCH') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {

        return bankBranchService.delete(id);
    }
}
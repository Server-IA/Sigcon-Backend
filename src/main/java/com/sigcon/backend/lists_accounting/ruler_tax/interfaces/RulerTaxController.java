package com.sigcon.backend.lists_accounting.ruler_tax.interfaces;

import com.sigcon.backend.lists_accounting.ruler_tax.application.AssignAccountingAccountToRulerTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.application.CreateRuleTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.application.UpdateRuleTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.service.RuleTaxService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ruler-tax")
@RequiredArgsConstructor
@Tag(name = "2. Módulo de Listas Contables - Reglas de impuesto", description = "Endpoints para la gestión de reglas de impuesto")

public class RulerTaxController {

    private final RuleTaxService ruleTaxService;

    @PostMapping("/create")
    @Operation(summary = "Crear regla de impuesto", description = "Crear una nueva regla de impuesto, requiere permisos de creación (PERM_CREATE_RULER_TAX) o rol superadmin (ROLE_SUPERADMIN)")
    @ApiResponse(responseCode = "200", description = "Regla de impuesto creada correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateRuleTaxDTO.class)))
    @ApiResponse(responseCode = "400", description = "Error al crear la regla de impuesto")

    @PreAuthorize("hasAuthority('PERM_CREATE_RULER_TAX') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> createRulerTax(@Valid @RequestBody(required = false) CreateRuleTaxDTO createRuleTaxDTO,
            BindingResult bindingResult) {
        return ruleTaxService.create(createRuleTaxDTO, bindingResult);
    }

    @Operation(summary = "Consultar reglas de impuesto para DataTable", description = "Retorna una lista paginada de reglas de impuesto compatible con DataTables, permitiendo filtros.")
    @ApiResponse(responseCode = "200", description = "Reglas de impuesto encontradas correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DataTableResponse.class)))
    @ApiResponse(responseCode = "400", description = "Error al consultar las reglas de impuesto")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "Sin permiso PERM_VIEW_RULER_TAX")
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_RULER_TAX') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> findAllPagedRulerTax(@RequestBody(required = false) DataTableRequest request) {
        System.out.println("request ruler tax: " + request);
        return ruleTaxService.findAllPaged(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar regla de impuesto", description = "Actualizar una regla de impuesto, requiere permisos de actualización (PERM_UPDATE_RULER_TAX) o rol superadmin (ROLE_SUPERADMIN)")
    @ApiResponse(responseCode = "200", description = "Regla de impuesto actualizada correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateRuleTaxDTO.class)))
    @ApiResponse(responseCode = "400", description = "Error al actualizar la regla de impuesto")
    @PreAuthorize("hasAuthority('PERM_UPDATE_RULER_TAX') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> updateRulerTax(@PathVariable Long id,
            @Valid @RequestBody(required = false) UpdateRuleTaxDTO updateRuleTaxDTO, BindingResult bindingResult) {
        return ruleTaxService.updateRuleTax(id, updateRuleTaxDTO, bindingResult);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar regla de impuesto", description = "Eliminar una regla de impuesto, requiere permisos de eliminación (PERM_DELETE_RULER_TAX) o rol superadmin (ROLE_SUPERADMIN)")
    @ApiResponse(responseCode = "200", description = "Regla de impuesto eliminada correctamente")
    @ApiResponse(responseCode = "400", description = "Error al eliminar la regla de impuesto")
    @PreAuthorize("hasAuthority('PERM_DELETE_RULER_TAX') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> deleteRulerTax(@PathVariable Long id) {
        return ruleTaxService.deleteRuleTax(id);
    }

    @PostMapping("/accounting-accounts")
    @Operation(summary = "Asignar cuenta contable a regla de impuesto", description = "Asignar una cuenta contable a una regla de impuesto, requiere permisos de asignación (PERM_ASSIGN_ACCOUNTING_ACCOUNT_TO_RULER_TAX) o rol superadmin (ROLE_SUPERADMIN)")
    @ApiResponse(responseCode = "200", description = "Cuenta contable asignada correctamente")
    @ApiResponse(responseCode = "400", description = "Error al asignar la cuenta contable")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_ACCOUNTING_ACCOUNT_TO_RULER_TAX') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> assignAccountingAccountToRulerTax(
            @Valid @RequestBody(required = false) AssignAccountingAccountToRulerTaxDTO assignAccountingAccountToRulerTaxDTO,
            BindingResult bindingResult) {
        return ruleTaxService.assignAccountingAccountToRulerTax(assignAccountingAccountToRulerTaxDTO, bindingResult);
    }
}

package com.sigcon.backend.nomina.interfaces.controller;

import com.sigcon.backend.nomina.application.CreateEmployeeRequest;
import com.sigcon.backend.nomina.domain.service.EmployeeService;
import com.sigcon.backend.utils.DataTableRequest;
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
 * HU-NOM-01: REST de empleados de nomina.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/nomina/empleados} - listar todos (sin paginar)</li>
 *   <li>{@code POST /api/nomina/empleados/search} - DataTable paginado</li>
 *   <li>{@code GET /api/nomina/empleados/{id}} - detalle</li>
 *   <li>{@code GET /api/nomina/empleados/{id}/historial-salarial} - historial de cambios</li>
 *   <li>{@code POST /api/nomina/empleados} - crear</li>
 *   <li>{@code PUT /api/nomina/empleados/{id}} - actualizar (exige motivo si cambia salario)</li>
 *   <li>{@code DELETE /api/nomina/empleados/{id}} - soft delete</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/nomina/empleados")
@RequiredArgsConstructor
@Tag(name = "Nomina - Empleados",
     description = "CRUD empleados + historial salarial + validacion SMLV (HU-NOM-01)")
public class EmployeeController {

    private final EmployeeService service;

    @Operation(summary = "Listar empleados (sin paginar)",
            description = "Devuelve todos los empleados activos. Para volumenes grandes usar /search.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de empleados"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping
    public ResponseEntity<?> list() {
        return service.list();
    }

    @Operation(summary = "Busqueda paginada de empleados (DataTable)",
            description = "Endpoint para DataTableReference del frontend. Soporta busqueda global, "
                    + "filtros por columna y paginacion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de empleados"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        if (request == null) request = new DataTableRequest();
        return service.search(request);
    }

    @Operation(summary = "Obtener empleado por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado encontrado"),
            @ApiResponse(responseCode = "400", description = "Empleado no existe")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            @Parameter(description = "ID del empleado", example = "1") @PathVariable Long id) {
        return service.getById(id);
    }

    @Operation(summary = "Historial salarial del empleado (HU-NOM-01 E3)",
            description = "Lista cronologica descendente de los cambios salariales con motivo "
                    + "y fecha efectiva.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial salarial"),
            @ApiResponse(responseCode = "400", description = "Empleado no existe")
    })
    @GetMapping("/{id}/historial-salarial")
    public ResponseEntity<?> salaryHistory(
            @Parameter(description = "ID del empleado", example = "1") @PathVariable Long id) {
        return service.getSalaryHistory(id);
    }

    @Operation(summary = "Crear empleado (HU-NOM-01)",
            description = "Valida SMLV vigente (HU-NOM-01 E2) + unicidad por (documentType,documentNumber).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado creado"),
            @ApiResponse(responseCode = "400", description = "Salario < SMLV, documento duplicado o datos invalidos")
    })
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateEmployeeRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Actualizar empleado",
            description = "Si se modifica baseSalary, el campo salaryChangeReason es obligatorio "
                    + "(HU-NOM-01 E3). Cada cambio genera una entrada en el historial salarial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado actualizado"),
            @ApiResponse(responseCode = "400", description = "Salario < SMLV, falta motivo o empleado inexistente")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "ID del empleado", example = "1") @PathVariable Long id,
            @Valid @RequestBody CreateEmployeeRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Eliminar empleado (soft delete)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado eliminado"),
            @ApiResponse(responseCode = "400", description = "Empleado no existe")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "ID del empleado", example = "1") @PathVariable Long id) {
        return service.delete(id);
    }
}

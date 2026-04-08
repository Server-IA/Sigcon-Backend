package com.sigcon.backend.banks.banks.interfaces;

import com.sigcon.backend.banks.banks.application.BankDTO;
import com.sigcon.backend.banks.banks.domain.service.BankService;
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
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
@Tag(name = "5. Módulo de Bancos - Bancos", description = "Endpoints para gestion de bancos")
@SecurityRequirement(name = "bearerAuth")
public class BankController {

    private final BankService bankService;

    @PostMapping("/store")
    @Operation(
        summary = "Registrar banco",
        description = "Crea un banco con su información general.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Payload de creación del banco",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BankDTO.class),
                examples = @ExampleObject(value = """
    {
                    "code": "0043",
                    "name": "BANCO DE PRUEBA",
                    "nameShort": "BANCO PRUEBA",
                    "typeBank": "COMMERCIAL",
                    "nit": "9001234225632",
                    "swift": "COLOCOBOGXX2X23X",
                    "codeAch": "12342223",
                    "urlWebservice": "https://api.banco.com",
                    "conciliationDays": 3,
                    "phone": "6011234567",
                    "formatExtract": "TXT",
                    "countryId": 1
                    }
                    """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Banco registrado correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_CREATE_BANK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> store(
            @Valid @RequestBody(required = false) BankDTO request,
            BindingResult bindingResult) {

        return bankService.create(request, bindingResult);
    }

    @PostMapping("/search")
    @Operation(
        summary = "Consultar bancos",
        description = "Consulta paginada de bancos con filtros (DataTable).",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            description = "Objeto DataTableRequest con filtros y paginación.",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DataTableRequest.class),
                examples = @ExampleObject(value = """
                        {
                        "draw": 1,
                        "start": 0,
                        "length": 10,
                        "search": { "value": "BANCO", "regex": false },
                        "columns": [],
                        "order": []
                        }
                        """)
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente"),
        @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
        @ApiResponse(responseCode = "403", description = "Sin permisos"),
        @ApiResponse(responseCode = "404", description = "Sin resultados")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_BANK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {

        return bankService.findAllPaged(request);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Detalle de banco",
        description = "Retorna la información detallada de un banco por ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalle obtenido correctamente"),
        @ApiResponse(responseCode = "404", description = "Banco no encontrado"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_VIEW_BANK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> detail(@PathVariable Long id) {

        return bankService.getDetail(id);
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Actualizar banco",
        description = "Actualiza la información de un banco existente.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BankDTO.class),
                examples = @ExampleObject(value = """
{
  "name": "BANCO DE PRUEBA ACTUALIZADO",
  "phone": "6017654321",
  "status": 1
}
""")
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Banco actualizado correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación"),
        @ApiResponse(responseCode = "404", description = "Banco no encontrado"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_UPDATE_BANK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody BankDTO request,
            BindingResult bindingResult) {

        return bankService.update(id, request, bindingResult);
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Eliminar banco",
        description = "Eliminación lógica del banco."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Banco eliminado correctamente"),
        @ApiResponse(responseCode = "404", description = "Banco no encontrado"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_DELETE_BANK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {

        return bankService.delete(id);
    }
}
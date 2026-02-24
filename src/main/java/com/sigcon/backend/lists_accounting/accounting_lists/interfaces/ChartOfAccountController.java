package com.sigcon.backend.lists_accounting.accounting_lists.interfaces;

import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.CreateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.DeleteChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.UpdateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.ViewChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.service.ChartOfAccountService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/chart-of-accounts")
@RequiredArgsConstructor
@Tag(name = "Accounting Lists - Chart Of Accounts", description = "Endpoints para gestionar el catalogo de cuentas contables (PUC)")
public class ChartOfAccountController {

    private final ChartOfAccountService chartOfAccountService;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleJsonParseError(HttpMessageNotReadableException ex) {
        Throwable rootCause = ex.getMostSpecificCause();
        return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(rootCause.getMessage()))
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CREATE_CHART_OF_ACCOUNT')")
    @Operation(
            summary = "Crear una cuenta contable",
            description = "Crea una nueva cuenta dentro del catalogo PUC con sus datos base."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Cuenta creada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> createChartOfAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos requeridos para crear la cuenta contable."
            )
            @Valid @RequestBody CreateChartOfAccountDTO request,
            @Parameter(hidden = true)
            BindingResult bindingResult
    ) {
        try {
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            chartOfAccountService.createChartOfAccount(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("La cuenta ha sido creada exitosamente en el catalogo PUC"),
                            Optional.empty()
                    )
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Error al guardar la informacion, intente nuevamente"
                    )));
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_CHART_OF_ACCOUNT')")
    @Operation(
            summary = "Buscar cuentas contables (paginado)",
            description = "Consulta cuentas por filtros opcionales y retorna resultados paginados."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Consulta realizada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Filtros invalidos",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> searchChartOfAccounts(
            @ParameterObject @Valid @ModelAttribute ViewChartOfAccountDTO request,
            @Parameter(hidden = true)
            BindingResult bindingResult,
            @Parameter(description = "Numero de pagina (inicia en 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamano de pagina permitido: 10, 20, 50 o 100", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            Pageable pageable = PageRequest.of(Math.max(page, 0), resolvePageSize(size));
            Page<ChartOfAccountResponseDTO> result = chartOfAccountService.searchChartOfAccounts(request, pageable);

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Se encontraron cuentas con coincidencias"),
                            Optional.of(result)
                    )
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Error al consultar datos, intente nuevamente"
                    )));
        }
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_CHART_OF_ACCOUNT')")
    @Operation(
            summary = "Buscar cuentas contables (DataTable)",
            description = "Consulta cuentas contables usando el formato de request de DataTables."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Consulta DataTable realizada correctamente",
                    content = @Content(schema = @Schema(implementation = DataTableResponse.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> searchChartOfAccountsDataTable(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = false,
                    description = "Payload opcional con formato DataTables para paginacion, busqueda y filtros."
            )
            @RequestBody(required = false) DataTableRequest request
    ) {
        try {
            DataTableResponse<ChartOfAccountResponseDTO> result = chartOfAccountService.searchChartOfAccounts(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Error al consultar datos, intente nuevamente"
                    )));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_CHART_OF_ACCOUNT')")
    @Operation(
            summary = "Actualizar una cuenta contable",
            description = "Actualiza los datos principales de una cuenta existente en el catalogo PUC."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cuenta actualizada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o regla de negocio incumplida",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> updateChartOfAccount(
            @Parameter(description = "ID de la cuenta contable a actualizar", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Datos requeridos para actualizar la cuenta contable."
            )
            @Valid @RequestBody UpdateChartOfAccountDTO request,
            @Parameter(hidden = true)
            BindingResult bindingResult
    ) {
        try {
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            chartOfAccountService.updateChartOfAccount(request, id);
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("La cuenta fue actualizada exitosamente"),
                            Optional.empty()
                    )
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Error al guardar la informacion, intente nuevamente"
                    )));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_CHART_OF_ACCOUNT')")
    @Operation(
            summary = "Inactivar o eliminar logica de una cuenta contable",
            description = "Registra la eliminacion logica de una cuenta con su motivo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cuenta eliminada/inactivada correctamente",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o regla de negocio incumplida",
                    content = @Content(schema = @Schema(implementation = Object.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<?> deleteChartOfAccount(
            @Parameter(description = "ID de la cuenta contable a eliminar", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Motivo de eliminacion/inactivacion de la cuenta."
            )
            @Valid @RequestBody DeleteChartOfAccountDTO request,
            @Parameter(hidden = true)
            BindingResult bindingResult
    ) {
        try {
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            chartOfAccountService.deleteChartOfAccount(id, request);
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("La cuenta ha sido eliminada exitosamente"),
                            Optional.empty()
                    )
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Error al registrar la inactivacion. Intente nuevamente mas tarde"
                    )));
        }
    }

    private int resolvePageSize(int requestedSize) {
        return switch (requestedSize) {
            case 10, 20, 50, 100 -> requestedSize;
            default -> 10;
        };
    }
}

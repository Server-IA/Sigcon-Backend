package com.sigcon.backend.lists_accounting.accounting_lists.interfaces;

import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.CreateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.DeleteChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.UpdateChartOfAccountDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/chart-of-accounts")
@RequiredArgsConstructor
@Tag(name = "2. Módulo de Listas Contables - Catalogo de cuentas (PUC)", description = "Endpoints para gestionar el catalogo de cuentas (PUC)")
public class ChartOfAccountController {

        private final ChartOfAccountService chartOfAccountService;

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<?> handleJsonParseError(HttpMessageNotReadableException ex) {
                Throwable rootCause = ex.getMostSpecificCause();
                return ResponseEntity.badRequest().body(
                                ErrorRespondJson.getErrorRespondMessage(Optional.of(rootCause.getMessage())));
        }

        @PostMapping
        @PreAuthorize("hasAuthority('PERM_CREATE_CHART_OF_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Crear una cuenta en el PUC", description = "Crea una nueva cuenta dentro del catalogo PUC con sus datos base.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Cuenta creada correctamente", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> createChartOfAccount(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Datos requeridos para crear la cuenta.") @Valid @RequestBody CreateChartOfAccountDTO request,
                        @Parameter(hidden = true) BindingResult bindingResult) {
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                chartOfAccountService.createChartOfAccount(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("La cuenta ha sido creada exitosamente en el catalogo PUC"),
                                                Optional.empty()));
        }

        @PostMapping("/search")
        @PreAuthorize("hasAuthority('PERM_VIEW_CHART_OF_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Buscar cuentas en el PUC (DataTable)", description = "Consulta cuentas en el PUC usando el formato de request de DataTables.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Consulta DataTable realizada correctamente", content = @Content(schema = @Schema(implementation = DataTableResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Solicitud invalida", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> searchChartOfAccountsDataTable(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, description = "Payload opcional con formato DataTables para paginacion, busqueda y filtros.") @RequestBody(required = false) DataTableRequest request) {
                try {
                        DataTableResponse<ChartOfAccountResponseDTO> result = chartOfAccountService
                                        .searchChartOfAccounts(request);
                        return ResponseEntity.ok(result);
                } catch (IllegalArgumentException | IllegalStateException e) {
                        return ResponseEntity.badRequest().body(
                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                                        "Error al consultar datos, intente nuevamente")));
                }
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_UPDATE_CHART_OF_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Actualizar una cuenta del PUC", description = "Actualiza los datos principales de una cuenta existente en el catalogo PUC.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Cuenta actualizada correctamente", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "400", description = "Datos invalidos o regla de negocio incumplida", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> updateChartOfAccount(
                        @Parameter(description = "ID de la cuenta del PUC a actualizar", example = "1") @PathVariable Long id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Datos requeridos para actualizar la cuenta del PUC.") @Valid @RequestBody UpdateChartOfAccountDTO request,
                        @Parameter(hidden = true) BindingResult bindingResult) {
                /* try { */
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                chartOfAccountService.updateChartOfAccount(request, id);
                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("La cuenta fue actualizada exitosamente"),
                                                Optional.empty()));
                /*
                 * } catch (IllegalArgumentException | IllegalStateException e) {
                 * return ResponseEntity.badRequest().body(
                 * ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
                 * );
                 * } catch (Exception e) {
                 * return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                 * .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                 * "Error al guardar la informacion, intente nuevamente"
                 * )));
                 * }
                 */
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_DELETE_CHART_OF_ACCOUNT') or hasAuthority('ROLE_SUPERADMIN')")
        @Operation(summary = "Eliminacion logica de una cuenta", description = "Registra la eliminacion logica de una cuenta con su motivo.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Cuenta eliminada/inactivada correctamente", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "400", description = "Datos invalidos o regla de negocio incumplida", content = @Content(schema = @Schema(implementation = Object.class))),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content(schema = @Schema(implementation = Object.class)))
        })
        public ResponseEntity<?> deleteChartOfAccount(
                        @Parameter(description = "ID de la cuenta del PUC a eliminar", example = "1") @PathVariable Long id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Motivo de eliminacion/inactivacion de la cuenta.") @Valid @RequestBody DeleteChartOfAccountDTO request,
                        @Parameter(hidden = true) BindingResult bindingResult) {
                /* try { */
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                chartOfAccountService.deleteChartOfAccount(id, request);
                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("La cuenta ha sido eliminada exitosamente"),
                                                Optional.empty()));
                /*
                 * } catch (IllegalArgumentException | IllegalStateException e) {
                 * return ResponseEntity.badRequest().body(
                 * ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
                 * );
                 * } catch (Exception e) {
                 * return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                 * .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                 * "Error al registrar la inactivacion. Intente nuevamente mas tarde"
                 * )));
                 * }
                 */
        }

}

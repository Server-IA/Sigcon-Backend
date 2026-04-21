package com.sigcon.backend.lists_accounting.types_of_currency.interfaces;

import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeRequestDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeUpdateRequestDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeDeleteResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.service.CurrencyTypeService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounting-lists/currency-types")
@RequiredArgsConstructor
@Tag(name = "2. Módulo de Listas Contables - Tipos de moneda", description = "Endpoints para la gestión de tipos de moneda: búsqueda paginada, creación, actualización y eliminación lógica.")
public class CurrencyTypeController {

        private final CurrencyTypeService currencyTypeService;

        @PostMapping("/search")
        @PreAuthorize("hasAuthority('PERM_VIEW_CURRENCY_TYPE') or hasAuthority('ROLE_ADMIN')")
        @Operation(summary = "Consultar monedas para DataTable", description = "Retorna una lista paginada de monedas compatible con DataTables, permitiendo filtros y ordenamiento.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Búsqueda realizada exitosamente", content = @Content(mediaType = "application/json")),
                        @ApiResponse(responseCode = "403", description = "No tiene permisos para ver monedas", content = @Content)
        })
        public ResponseEntity<?> getCurrencyTypesDataTable(
                        @Parameter(description = "Configuración de paginación y filtros de DataTable") @RequestBody(required = false) DataTableRequest request) {

                return currencyTypeService.getCurrencyTypesDataTable(request);
        }

        @PostMapping
        @PreAuthorize("hasAuthority('PERM_CREATE_CURRENCY_TYPE') or hasAuthority('ROLE_ADMIN')")
        @Operation(summary = "Registrar nueva moneda", description = "Permite crear un nuevo tipo de moneda validando que el código ISO no esté duplicado.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Moneda creada exitosamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrencyTypeResponseDTO.class))),
                        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o código ISO ya existente", content = @Content),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content)
        })
        public ResponseEntity<?> createCurrencyType(
                        @Valid @RequestBody CurrencyTypeRequestDTO request, BindingResult bindingResult) {
                return currencyTypeService.createCurrencyType(request, bindingResult);
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_UPDATE_CURRENCY_TYPE') or hasAuthority('ROLE_ADMIN')")
        @Operation(summary = "Actualizar moneda existente", description = "Modifica los datos de una moneda identificada por su ID.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Moneda actualizada correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrencyTypeResponseDTO.class))),
                        @ApiResponse(responseCode = "400", description = "Error en los datos enviados", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Moneda no encontrada", content = @Content)
        })
        public ResponseEntity<?> updateCurrencyType(
                        @Parameter(description = "ID único de la moneda a actualizar", example = "1") @PathVariable Long id,
                        @Valid @RequestBody CurrencyTypeUpdateRequestDTO request, BindingResult bindingResult) {
                return currencyTypeService.updateCurrencyType(id, request, bindingResult);
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_DELETE_CURRENCY_TYPE') or hasAuthority('ROLE_ADMIN')")
        @Operation(summary = "Eliminar moneda (Soft Delete)", description = "Realiza una eliminación lógica de la moneda, permitiendo su posterior reutilización del código ISO.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Moneda eliminada exitosamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrencyTypeDeleteResponseDTO.class))),
                        @ApiResponse(responseCode = "404", description = "No se encontró la moneda con el ID proporcionado", content = @Content),
                        @ApiResponse(responseCode = "409", description = "Conflicto al intentar eliminar la moneda", content = @Content)
        })
        public ResponseEntity<?> deleteCurrencyType(
                        @Parameter(description = "ID de la moneda a eliminar", example = "2") @PathVariable Long id) {
                try {
                        CurrencyTypeDeleteResponseDTO response = currencyTypeService.deleteCurrencyType(id);
                        return ResponseEntity.ok(
                                        SuccessRespondJson.getSuccessRespondMessage(Optional.of(response.getMessage()),
                                                        Optional.of(response)));
                } catch (java.util.NoSuchElementException e) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(
                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                } catch (IllegalStateException e) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
                    throw __tie;
                } catch (Exception e) {
                        log.error("Error técnico al eliminar la moneda: ", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(
                                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                                                        "Error al procesar la eliminación, contacte al administrador (código ERR-DB-01)")));
                }
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                String message = request.getMethod().equalsIgnoreCase("PUT")
                                ? "Datos inválidos. Verifique el formato de entrada"
                                : "Debe ingresar un código ISO válido (ej. USD) y un nombre de moneda";

                return ResponseEntity.badRequest().body(
                                ErrorRespondJson.getErrorRespondMessage(Optional.of(message)));
        }
}

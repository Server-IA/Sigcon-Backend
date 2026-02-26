package com.sigcon.backend.lists_accounting.types_of_currency.interfaces;

import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeRequestDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeUpdateRequestDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeDeleteResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.service.CurrencyTypeService;
import com.sigcon.backend.utils.DataTableRequest;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/accounting-lists/currency-types")
@RequiredArgsConstructor
@Tag(name = "Lista de Monedas", description = "Endpoints para la gestión de tipos de moneda (ISO 4217)")
public class CurrencyTypeController {

        private final CurrencyTypeService currencyTypeService;

        @PostMapping("/search")
        @PreAuthorize("hasAuthority('PERM_VIEW_CURRENCY_TYPE')")
        @Operation(summary = "Consultar monedas para DataTable", description = "Retorna una lista paginada de monedas compatible con DataTables, permitiendo filtros y ordenamiento.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Búsqueda realizada exitosamente", content = @Content(mediaType = "application/json")),
                        @ApiResponse(responseCode = "403", description = "No tiene permisos para ver monedas", content = @Content)
        })
        public ResponseEntity<?> getCurrencyTypesDataTable(
                        @Parameter(description = "Configuración de paginación y filtros de DataTable") @RequestBody(required = false) DataTableRequest request) {
                if (request == null) {
                        request = new DataTableRequest();
                        request.setLength(10); // default value
                        request.setStart(0);
                }
                return currencyTypeService.getCurrencyTypesDataTable(request);
        }

        @PostMapping
        @PreAuthorize("hasAuthority('PERM_CREATE_CURRENCY_TYPE')")
        @Operation(summary = "Registrar nueva moneda", description = "Permite crear un nuevo tipo de moneda validando que el código ISO no esté duplicado.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Moneda creada exitosamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrencyTypeResponseDTO.class))),
                        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o código ISO ya existente", content = @Content),
                        @ApiResponse(responseCode = "500", description = "Error interno del servidor", content = @Content)
        })
        public ResponseEntity<?> createCurrencyType(
                        @Valid @RequestBody CurrencyTypeRequestDTO request) {

                try {
                        CurrencyTypeResponseDTO response = currencyTypeService.createCurrencyType(request);

                        return ResponseEntity.status(HttpStatus.CREATED).body(
                                        Map.of(
                                                        "success", true,
                                                        "message", "El tipo de moneda ha sido creado exitosamente",
                                                        "data", response));

                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(
                                        Map.of(
                                                        "success", false,
                                                        "message", e.getMessage()));

                } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of(
                                                        "success", false,
                                                        "message",
                                                        "Error interno al registrar la moneda. Intente nuevamente o contacte soporte"));
                }
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_UPDATE_CURRENCY_TYPE')")
        @Operation(summary = "Actualizar moneda existente", description = "Modifica los datos de una moneda identificada por su ID.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Moneda actualizada correctamente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrencyTypeResponseDTO.class))),
                        @ApiResponse(responseCode = "400", description = "Error en los datos enviados", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Moneda no encontrada", content = @Content)
        })
        public ResponseEntity<?> updateCurrencyType(
                        @Parameter(description = "ID único de la moneda a actualizar", example = "1") @PathVariable Long id,
                        @Valid @RequestBody CurrencyTypeUpdateRequestDTO request) {

                try {
                        CurrencyTypeResponseDTO response = currencyTypeService.updateCurrencyType(id, request);

                        return ResponseEntity.ok(
                                        Map.of(
                                                        "success", true,
                                                        "message", "El tipo de moneda ha sido actualizado exitosamente",
                                                        "data", response));

                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(
                                        Map.of(
                                                        "success", false,
                                                        "message", e.getMessage()));

                } catch (Exception e) {
                        log.error("Error técnico al actualizar la moneda: ", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of(
                                                        "success", false,
                                                        "message",
                                                        "Error técnico al actualizar la moneda. Intente nuevamente o contacte al administrador"));
                }
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('PERM_DELETE_CURRENCY_TYPE')")
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
                                        Map.of(
                                                        "success", true,
                                                        "message", response.getMessage(),
                                                        "data", response));
                } catch (java.util.NoSuchElementException e) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                                        Map.of("success", false, "message", e.getMessage()));
                } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(
                                        Map.of("success", false, "message", e.getMessage()));
                } catch (IllegalStateException e) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                                        Map.of("success", false, "message", e.getMessage()));
                } catch (Exception e) {
                        log.error("Error técnico al eliminar la moneda: ", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("success", false, "message",
                                                        "Error al procesar la eliminación, contacte al administrador (código ERR-DB-01)"));
                }
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                String message = request.getMethod().equalsIgnoreCase("PUT")
                                ? "Datos inválidos. Verifique el formato de entrada"
                                : "Debe ingresar un código ISO válido (ej. USD) y un nombre de moneda";

                return ResponseEntity.badRequest().body(
                                Map.of(
                                                "success", false,
                                                "message", message));
        }
}

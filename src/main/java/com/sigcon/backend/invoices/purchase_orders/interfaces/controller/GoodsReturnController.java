package com.sigcon.backend.invoices.purchase_orders.interfaces.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.invoices.purchase_orders.domain.service.GoodsReceiptService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * RF-21/32 (Notas Tecnicas CXP, 2026-06-02): controlador REST del LISTADO
 * independiente de devoluciones de mercancia (DV-). El registro de la
 * devolucion sigue en {@code POST /api/v1/ap/receipts/{id}/return}; este
 * controlador solo expone la consulta paginada y el detalle, para que las
 * devoluciones tengan su propia vista (antes se reutilizaba el listado de
 * recepciones).
 */
@RestController
@RequestMapping("/api/v1/ap/returns")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Devoluciones", description = "Listado de devoluciones de mercancia (DV-)")
public class GoodsReturnController {

    private final GoodsReceiptService receiptService;

    /**
     * Lista las devoluciones (DV-) con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de devoluciones
     */
    @Operation(summary = "Consultar devoluciones", description = "Lista devoluciones de mercancia (DV-) con paginacion y filtros DataTable")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de devoluciones")
    })
    @PostMapping("")
    @PreAuthorize("hasAuthority('PERM_READ_GOODS_RECEIPT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> searchReturns(@RequestBody(required = false) DataTableRequest request) {
        return receiptService.getReturns(request);
    }

    /**
     * Detalle de una devolucion por su identificador.
     *
     * @param id identificador de la devolucion
     * @return datos de la devolucion con sus lineas
     */
    @Operation(summary = "Obtener devolucion", description = "Retorna los datos completos de una devolucion incluyendo sus lineas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devolucion encontrada"),
            @ApiResponse(responseCode = "400", description = "Devolucion no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_READ_GOODS_RECEIPT') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getReturnById(@PathVariable Long id) {
        try {
            return receiptService.getReturnById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}

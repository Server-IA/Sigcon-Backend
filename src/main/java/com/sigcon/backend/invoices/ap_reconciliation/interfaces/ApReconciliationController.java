package com.sigcon.backend.invoices.ap_reconciliation.interfaces;

import com.sigcon.backend.invoices.ap_reconciliation.domain.service.ApReconciliationService;
import com.sigcon.backend.utils.ErrorRespondJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * AP-09: Controlador REST para conciliacion de pagos AP con movimientos BNK.
 *
 * <p>Provee endpoints para listar pagos pendientes de conciliar, sugerir
 * candidatos BNK, y enlazar/desvincular pagos con movimientos.
 */
@RestController
@RequestMapping("/api/v1/ap/reconciliation")
@RequiredArgsConstructor
@Tag(name = "6. Cuentas por Pagar - Conciliacion BNK",
     description = "Conciliacion de pagos AP con movimientos financieros bancarios (AP-09)")
public class ApReconciliationController {

    private final ApReconciliationService service;

    @Operation(summary = "Listar pagos AP pendientes de conciliar",
               description = "Retorna pagos AP cuyo bankMovementId es nulo.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido")})
    @GetMapping("/unreconciled")
    @PreAuthorize("hasAuthority('PERM_READ_AP_PAYMENT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listUnreconciled() {
        return service.listUnreconciled();
    }

    @Operation(summary = "Sugerir candidatos de conciliacion para un pago",
               description = "Busca movimientos BNK con fecha +/-7 dias, monto dentro de 1% y cuenta coincidente. "
                           + "Devuelve lista ordenada por score descendente.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidatos propuestos"),
        @ApiResponse(responseCode = "400", description = "Pago no encontrado")
    })
    @GetMapping("/{paymentId}/candidates")
    @PreAuthorize("hasAuthority('PERM_READ_AP_PAYMENT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> suggestCandidates(@PathVariable Long paymentId) {
        try {
            return service.suggestCandidates(paymentId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Enlazar pago AP con movimiento BNK",
               description = "Conciliacion manual. Valida que el pago no este ya conciliado y que "
                           + "la cuenta bancaria coincida (cuando ambas estan definidas).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conciliacion exitosa"),
        @ApiResponse(responseCode = "400", description = "Pago o movimiento no encontrado, o ya conciliado")
    })
    @PostMapping("/{paymentId}/link/{movementId}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_AP_PAYMENT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> linkPayment(@PathVariable Long paymentId,
                                          @PathVariable Long movementId) {
        try {
            return service.linkPaymentToMovement(paymentId, movementId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Desvincular pago AP de movimiento BNK",
               description = "Revierte la conciliacion previa. Pone bankMovementId a null.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conciliacion revertida"),
        @ApiResponse(responseCode = "400", description = "Pago no encontrado o no estaba conciliado")
    })
    @DeleteMapping("/{paymentId}/unlink")
    @PreAuthorize("hasAuthority('PERM_UPDATE_AP_PAYMENT') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> unlinkPayment(@PathVariable Long paymentId) {
        try {
            return service.unlinkPayment(paymentId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}

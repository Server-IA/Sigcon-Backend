package com.sigcon.backend.vouchers.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.vouchers.domain.service.VoucherService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "5. Módulo de Contabilidad", description = "Endpoints para gestion de contabilidad")
public class VouchersController {

    private final VoucherService voucherService;

    @PostMapping("/search")
    @Operation(summary = "Buscar vouchers", description = "RF01 - Consulta paginada con filtros avanzados (DataTable).<br>Permiso requerido: PERM_SEARCH_VOUCHER o ROLE_ADMIN")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vouchers encontrados"),
        @ApiResponse(responseCode = "400", description = "Error al buscar vouchers")
    })
    // QA Bloque BJ Bug 5 (2026-05-17): el endpoint se usa desde la pantalla
    // de conciliacion bancaria para listar comprobantes disponibles a
    // emparejar. Sin esto un usuario con perms de BNK.CONCILIACION pero sin
    // PERM_SEARCH_VOUCHER recibe 403. Ampliamos para aceptar tambien los
    // perms de conciliacion/movimientos VER.
    @PreAuthorize("hasAnyAuthority('PERM_SEARCH_VOUCHER','TEMP_PERM_SEARCH_VOUCHER','TEMP_SEARCH_VOUCHER','PERM_BNK.CONCILIACION.VER','TEMP_PERM_BNK.CONCILIACION.VER','TEMP_BNK.CONCILIACION.VER','PERM_BNK.MOVIMIENTOS.VER','TEMP_PERM_BNK.MOVIMIENTOS.VER','TEMP_BNK.MOVIMIENTOS.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        return voucherService.getVouchers(request);
    }
}

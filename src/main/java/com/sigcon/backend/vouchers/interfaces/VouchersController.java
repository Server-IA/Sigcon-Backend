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
    @PreAuthorize("hasAuthority('PERM_SEARCH_VOUCHER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        return voucherService.getVouchers(request);
    }
}

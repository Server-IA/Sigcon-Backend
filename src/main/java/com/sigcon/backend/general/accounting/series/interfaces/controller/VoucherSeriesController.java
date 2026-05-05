package com.sigcon.backend.general.accounting.series.interfaces.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.general.accounting.series.application.CreateVoucherSeriesRequest;
import com.sigcon.backend.general.accounting.series.domain.service.VoucherSeriesService;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * HU-CG-03A E3/E5: controlador REST para configuracion de series de
 * consecutivos por tipo de comprobante. Solo ADMIN o usuarios con
 * PERM_UPDATE_JOURNAL_ENTRY pueden gestionar series.
 */
@RestController
@RequestMapping("/api/v1/voucher-series")
@RequiredArgsConstructor
@Tag(name = "9. Contabilidad General - Series",
     description = "HU-CG-03A: Configuracion de rangos y prefijos de consecutivos por tipo de comprobante")
public class VoucherSeriesController {

    private final VoucherSeriesService service;

    @Operation(summary = "Listar series de consecutivos",
               description = "HU-CG-03A E3: retorna todas las series configuradas con porcentaje "
                           + "de uso y flag de alerta para que la UI muestre barras de progreso.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado obtenido")})
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> findAll() {
        return service.findAll();
    }

    @Operation(summary = "Detalle de serie")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Serie obtenida"),
        @ApiResponse(responseCode = "400", description = "Serie no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_VIEW_ACCOUNTING') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> findById(@PathVariable Long id) {
        try {
            return service.findById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Crear nueva serie",
               description = "HU-CG-03A E3: crea una nueva configuracion de consecutivos para un tipo "
                           + "de comprobante. Valida unicidad por tipo dentro de la empresa.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Serie creada"),
        @ApiResponse(responseCode = "400", description = "Validacion fallida (rango invalido, tipo duplicado)")
    })
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateVoucherSeriesRequest req) {
        try {
            return service.create(req);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Actualizar serie",
               description = "HU-CG-03A E3/E5: amplia el rango o ajusta el umbral de alerta. "
                           + "NO permite retroceder current_number (preserva consecutivos asignados).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Serie actualizada"),
        @ApiResponse(responseCode = "400", description = "Validacion fallida")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id,
                                     @Valid @RequestBody CreateVoucherSeriesRequest req) {
        try {
            return service.update(id, req);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    @Operation(summary = "Eliminar serie",
               description = "HU-CG-03A E3: soft delete. Si la serie esta en uso por algun comprobante, "
                           + "deberia inactivarse via update con status=INACTIVE en lugar de eliminar.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Serie eliminada"),
        @ApiResponse(responseCode = "400", description = "Serie no encontrada")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return service.delete(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}

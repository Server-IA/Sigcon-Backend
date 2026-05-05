package com.sigcon.backend.lists_accounting.exchangeRates.infrastructure.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.CreateExchangeRateRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.ExchangeRateFilterRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.UpdateExchangeRateRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.service.ExchangeRateService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/exchange-rates")
@RequiredArgsConstructor
@Tag(name = "2. Módulo de Listas Contables - Tasas de cambio", description = "Endpoints para la gestión de tasas de cambio")
public class ExchangeRateController {

    private final ExchangeRateService service;

    @PostMapping
    @Operation(summary = "Crear una nueva tasa de cambio", description = "Crea una nueva tasa de cambio en la base de datos")
    @PreAuthorize("hasAuthority('PERM_CREATE_EXCHANGE_RATES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> create(@RequestBody CreateExchangeRateRequest request, BindingResult bindingResult) {
        return service.create(request, bindingResult);
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXCHANGE_RATES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Consultar tasas de cambio para DataTable", description = "Retorna una lista paginada de tasas de cambio compatible con DataTables, permitiendo filtros.")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest filter) {
        return service.findAll(filter);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una tasa de cambio", description = "Actualiza una tasa de cambio en la base de datos")
    @PreAuthorize("hasAuthority('PERM_UPDATE_EXCHANGE_RATES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody UpdateExchangeRateRequest request,
            BindingResult bindingResult) {
        return service.update(id, request, bindingResult);
    }

    /** HU-CFG-RF-25 E3: exporta tasas de cambio en CSV o XLSX. */
    @org.springframework.web.bind.annotation.GetMapping("/export/{format}")
    @Operation(summary = "Exportar tasas de cambio",
               description = "HU-CFG-RF-25 E3: descarga listado en CSV o XLSX (multi-tenant)")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXCHANGE_RATES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<byte[]> exportExchangeRates(@org.springframework.web.bind.annotation.PathVariable String format) {
        byte[] data = service.exportAll(format);
        String mime = "xlsx".equalsIgnoreCase(format)
                ? com.sigcon.backend.utils.export.SimpleTableExporter.XLSX_MIME
                : com.sigcon.backend.utils.export.SimpleTableExporter.CSV_MIME;
        String fname = "tasas_cambio." + ("xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv");
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, mime)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fname + "\"")
                .body(data);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una tasa de cambio (soft delete)",
               description = "HU-CFG-RF-28: requiere motivo (minimo 10 caracteres). Bloquea eliminacion si la tasa esta vigente.")
    @PreAuthorize("hasAuthority('PERM_DELETE_EXCHANGE_RATES') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(value = "reason", required = false) String reason) {
        return service.delete(id, reason);
    }
}

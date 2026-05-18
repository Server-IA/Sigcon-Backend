package com.sigcon.backend.accounts_receivable.sales_invoices.interfaces;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.accounts_receivable.sales_invoices.application.CreateSalesInvoiceRequest;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.service.SalesInvoiceExportService;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.service.SalesInvoiceService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST del modulo Cuentas por Cobrar (AR) - Facturas de Venta.
 * Cubre HUs AR-01A, AR-01B, AR-04, AR-11 y AR-13.
 */
@RestController
@RequestMapping("/api/v1/sales-invoices")
@RequiredArgsConstructor
@Tag(name = "7. Cuentas por Cobrar - Facturas",
     description = "Endpoints para gestion de facturas de venta (FV)")
public class SalesInvoicesController {

    private final SalesInvoiceService service;
    private final SalesInvoiceExportService exportService;

    /**
     * AR-01A: Crea una nueva factura de venta con calculo de impuestos y retenciones.
     */
    @Operation(summary = "Crear factura de venta FV",
               description = "Crea una factura de venta con lineas, calculo automatico de IVA y retenciones (motor UVT) y asiento contable")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/fv")
    @PreAuthorize("hasAuthority('PERM_CREATE_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> createSalesInvoice(
            @Valid @RequestBody CreateSalesInvoiceRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        try {
            SalesInvoice invoice = service.createSalesInvoice(request);
            return service.getById(invoice.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * AR-01B: Consulta paginada de facturas de venta (DataTable).
     */
    @Operation(summary = "Consultar facturas de venta",
               description = "Lista facturas de venta con paginacion y filtros DataTable")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado obtenido correctamente")
    })
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        return service.search(request);
    }

    /**
     * AR-01B: Obtiene una factura por ID.
     */
    @Operation(summary = "Obtener factura por ID",
               description = "Retorna los datos completos de una factura de venta con sus lineas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura encontrada"),
        @ApiResponse(responseCode = "400", description = "Factura no encontrada")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return service.getById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Actualiza campos editables de una factura de venta.
     */
    @Operation(summary = "Actualizar factura de venta",
               description = "Actualiza campos editables de una factura (notas, forma de pago, fecha vencimiento, resolucion)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura actualizada"),
        @ApiResponse(responseCode = "400", description = "Error de validacion o regla de negocio")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody CreateSalesInvoiceRequest request) {
        try {
            return service.update(id, request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * AR-06: Anula una factura de venta y reversa su asiento contable.
     */
    @Operation(summary = "Anular factura de venta",
               description = "Cambia el estado a VOIDED y reversa el asiento contable asociado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura anulada"),
        @ApiResponse(responseCode = "400", description = "No se puede anular (tiene pagos o ya esta anulada)")
    })
    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('PERM_DELETE_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> voidInvoice(@PathVariable Long id) {
        try {
            return service.voidInvoice(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * AR-06: Dispara manualmente la actualizacion de estado OVERDUE sobre facturas vencidas.
     */
    @Operation(summary = "Actualizar facturas vencidas",
               description = "Marca como OVERDUE todas las facturas con saldo pendiente y fecha vencida (ejecucion manual del scheduler)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Actualizacion ejecutada")
    })
    @PostMapping("/update-overdue")
    @PreAuthorize("hasAuthority('PERM_UPDATE_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> updateOverdue() {
        int count = service.updateOverdueInvoices();
        return ResponseEntity.ok(
                com.sigcon.backend.utils.SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Se actualizaron " + count + " facturas a OVERDUE"),
                        Optional.of(count)));
    }

    /**
     * HU-AR-06 E1 + E3: dispara la reconciliacion integral de estados de facturas
     * de venta (ejecucion manual del scheduler 1:30 AM). Corrige status segun
     * balanceDue real (PAID si saldo=0, OVERDUE si vencida con saldo, etc.).
     */
    @Operation(summary = "Reconciliar estados de facturas (manual)",
               description = "HU-AR-06: ejecuta la reconciliacion integral de estados (PAID/PARTIALLY_PAID/OVERDUE/ISSUED) segun balanceDue real")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reconciliacion ejecutada")
    })
    @PostMapping("/reconcile-statuses")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> reconcileStatuses() {
        int count = service.reconcileInvoiceStatuses();
        return ResponseEntity.ok(
                com.sigcon.backend.utils.SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Reconciliacion ejecutada: " + count + " facturas con status corregido"),
                        Optional.of(count)));
    }

    /**
     * Elimina logicamente una factura de venta.
     */
    @Operation(summary = "Eliminar factura de venta",
               description = "Elimina logicamente una factura. No permitido si tiene pagos o liquidada")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Factura eliminada"),
        @ApiResponse(responseCode = "400", description = "Regla de negocio incumplida")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return service.delete(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * QA Bloque BN (2026-05-18): exportar el listado de facturas de venta al
     * formato pedido (csv o xlsx). Reemplaza el export cliente del DataTable
     * (que producia archivos sin header empresa y sin totales) por un export
     * server-side con formato unificado: empresa+NIT, usuario+rol, fecha de
     * generacion, filtros aplicados, listado con estado traducido y fila TOTAL
     * con sumatorias.
     *
     * <p>Filtros opcionales (todos como query params):
     * <ul>
     *   <li>{@code status}: codigo del enum (DRAFT, ISSUED, PAID, ...).</li>
     *   <li>{@code dateFrom}/{@code dateTo}: rango fecha emision yyyy-MM-dd.</li>
     *   <li>{@code thirdPartyId}: filtrar por cliente.</li>
     * </ul>
     */
    @Operation(summary = "Exportar listado de facturas de venta (CSV/XLSX)",
               description = "Genera el archivo con encabezado estandar (empresa, usuario, filtros) + fila TOTAL con sumatorias.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo generado"),
        @ApiResponse(responseCode = "400", description = "Formato no soportado")
    })
    @GetMapping("/export/{format}")
    @PreAuthorize("hasAuthority('PERM_READ_SALES_INVOICE') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> exportListing(
            @PathVariable String format,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    java.time.LocalDate dateFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
                    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    java.time.LocalDate dateTo,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long thirdPartyId) {
        try {
            SalesInvoiceExportService.ExportResult res = exportService.exportListing(
                    format, status, dateFrom, dateTo, thirdPartyId);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType(res.mime));
            headers.setContentDispositionFormData("attachment", res.fileName);
            return new ResponseEntity<>(res.content, headers, org.springframework.http.HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }
}

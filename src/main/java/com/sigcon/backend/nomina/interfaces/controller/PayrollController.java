package com.sigcon.backend.nomina.interfaces.controller;

import com.sigcon.backend.nomina.application.LiquidatePayrollRequest;
import com.sigcon.backend.nomina.application.PayrollReceiptDTO;
import com.sigcon.backend.nomina.application.UpdatePayrollReceiptRequest;
import com.sigcon.backend.nomina.domain.model.PayrollReceipt;
import com.sigcon.backend.nomina.domain.repository.PayrollReceiptRepository;
import com.sigcon.backend.nomina.domain.service.PayrollService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HU-NOM-03 / HU-NOM-04: liquidacion periodica + flujo de aprobacion.
 *
 * <p>Liquidacion masiva por grupo de empleados. Recibos individuales con estados
 * DRAFT -> APPROVED -> CLOSED (inmutable).
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/nomina/recibos")
@RequiredArgsConstructor
@Tag(name = "Nomina - Liquidacion",
     description = "Liquidacion periodica + flujo DRAFT/APPROVED/CLOSED (HU-NOM-03/04)")
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollReceiptRepository receiptRepository;

    /** Builder reutilizable para /search. */
    private final DataTableSpecificationBuilder<PayrollReceipt> specBuilder = new DataTableSpecificationBuilder<>();

    @Operation(summary = "Liquidar nomina del periodo (HU-NOM-03)",
            description = "Calcula devengados, deducciones y aportes patronales para todos los "
                    + "empleados del filtro. Genera un JournalEntry consolidado. Los empleados "
                    + "sin EPS o fondo de pension son excluidos con mensaje de error y se "
                    + "reportan en el campo 'excluded' (HU-NOM-03 E3). Si todo OK, los recibos "
                    + "quedan en DRAFT listos para aprobar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Liquidacion completada: totalReceipts + journalEntryId + receipts + excluded"),
            @ApiResponse(responseCode = "400", description = "Error: periodo cerrado, sin empleados, datos invalidos")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.CREAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/liquidar")
    public ResponseEntity<?> liquidate(@Valid @RequestBody LiquidatePayrollRequest req) {
        return ResponseEntity.ok(payrollService.liquidatePeriod(req));
    }

    @Operation(summary = "Listar recibos del periodo",
            description = "Sin parametros year/month devuelve lista vacia. Con year+month "
                    + "devuelve todos los recibos del periodo ordenados cronologicamente.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Lista de recibos"))
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping
    public ResponseEntity<?> list(
            @Parameter(description = "Año del periodo", example = "2026")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "Mes del periodo (1-12)", example = "4")
            @RequestParam(required = false) Integer month) {
        if (year == null || month == null) return ResponseEntity.ok(List.of());
        List<PayrollReceiptDTO> data = payrollService.getByPeriod(year, month);
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "Busqueda paginada de recibos (DataTable)",
            description = "Para DataTableReference del frontend. Soporta filtros por columna "
                    + "(periodYear, periodMonth, status, employeeId) y paginacion.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Pagina de recibos"))
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        if (request == null) request = new DataTableRequest();
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;
        Pageable pageable = length == -1 ? Pageable.unpaged() : PageRequest.of(page, safeLength);

        Specification<PayrollReceipt> spec = specBuilder.build(request);
        Page<PayrollReceipt> data = receiptRepository.findAll(spec, pageable);
        Page<PayrollReceiptDTO> mapped = data.map(r -> PayrollReceiptDTO.from(r, null, null, List.of()));
        return ResponseEntity.ok(DataTableResponse.from(mapped, request.getDraw()));
    }

    @Operation(summary = "Obtener detalle de recibo con lineas",
            description = "Devuelve cabecera + todas las lineas (devengados, deducciones, aportes).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recibo encontrado"),
            @ApiResponse(responseCode = "400", description = "Recibo no existe")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            @Parameter(description = "ID del recibo", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getById(id));
    }

    @Operation(summary = "Actualizar campos editables del recibo (HU-NOM-04 E2)",
            description = "Permite modificar `daysWorked` y `notes` de un recibo en estado DRAFT. "
                    + "Si el recibo esta en APPROVED o CLOSED, rechaza con el mensaje exacto del Excel: "
                    + "\"La nómina está [APROBADA/CERRADA] y no puede modificarse. Para corregir "
                    + "errores, cree una nómina complementaria o de ajuste\". Los totales no son "
                    + "editables directamente; para recalcular hay que re-liquidar el periodo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recibo actualizado"),
            @ApiResponse(responseCode = "400",
                    description = "Recibo no existe o esta en APPROVED/CLOSED (mensaje exacto HU-NOM-04 E2)")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "ID del recibo", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdatePayrollReceiptRequest req) {
        return ResponseEntity.ok(payrollService.updateReceipt(id, req.getDaysWorked(), req.getNotes()));
    }

    @Operation(summary = "Aprobar recibo (HU-NOM-04 E1)",
            description = "Cambia status DRAFT -> APPROVED. El JournalEntry asociado pasa a POSTED. "
                    + "A partir de aqui el recibo es inmutable (HU-NOM-04 E2).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recibo aprobado"),
            @ApiResponse(responseCode = "400", description = "Recibo no existe o no esta en DRAFT")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.APROBAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @Parameter(description = "ID del recibo", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(payrollService.approve(id));
    }

    @Operation(summary = "Cerrar recibo definitivo (HU-NOM-04 E3)",
            description = "Cambia status APPROVED -> CLOSED. Inmutable. Para correcciones "
                    + "posteriores usar nomina complementaria.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recibo cerrado"),
            @ApiResponse(responseCode = "400", description = "Recibo no existe o no esta en APPROVED")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.CERRAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/{id}/close")
    public ResponseEntity<?> close(
            @Parameter(description = "ID del recibo", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(payrollService.close(id));
    }

    /** HU-NOM-03 DEF#2 (2026-04-28): editar amount de una linea SOLO en DRAFT. */
    @Operation(summary = "Actualizar valor de linea (HU-NOM-03 DEF#2)",
            description = "Modifica el amount de una linea de un recibo en estado DRAFT y "
                    + "recalcula los totales. Inmutable en APPROVED/CLOSED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Linea actualizada"),
            @ApiResponse(responseCode = "400", description = "Recibo no en DRAFT, linea inexistente o monto invalido")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PutMapping("/{id}/lineas/{lineId}")
    public ResponseEntity<?> updateLine(
            @PathVariable Long id, @PathVariable Long lineId,
            @RequestBody java.util.Map<String, Object> body) {
        java.math.BigDecimal amount = body.get("amount") != null
                ? new java.math.BigDecimal(body.get("amount").toString())
                : java.math.BigDecimal.ZERO;
        return ResponseEntity.ok(payrollService.updateLine(id, lineId, amount));
    }

    /** HU-NOM-03 DEF#2 (2026-04-28): eliminar linea SOLO en DRAFT. */
    @Operation(summary = "Eliminar linea (HU-NOM-03 DEF#2)",
            description = "Soft-delete de una linea de recibo en DRAFT y recalcula totales.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Linea eliminada"),
            @ApiResponse(responseCode = "400", description = "Recibo no en DRAFT o linea inexistente")
    })
    @PreAuthorize("hasAuthority('PERM_NOM.LIQUIDACION.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @DeleteMapping("/{id}/lineas/{lineId}")
    public ResponseEntity<?> deleteLine(@PathVariable Long id, @PathVariable Long lineId) {
        return ResponseEntity.ok(payrollService.deleteLine(id, lineId));
    }
}

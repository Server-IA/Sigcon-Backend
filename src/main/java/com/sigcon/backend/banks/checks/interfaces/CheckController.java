package com.sigcon.backend.banks.checks.interfaces;

import com.sigcon.backend.banks.checks.application.EmitCheckRequest;
import com.sigcon.backend.banks.checks.application.ReconcileCheckRequest;
import com.sigcon.backend.banks.checks.application.ReportLostCheckRequest;
import com.sigcon.backend.banks.checks.application.VoidCheckRequest;
import com.sigcon.backend.banks.checks.domain.service.CheckService;
import com.sigcon.backend.utils.DataTableRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag; 
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/banks/checks")
@RequiredArgsConstructor
@Tag(name = "5. Módulo de Bancos - Cheques", description = "Endpoints para emision, consulta y gestion de cheques")
@SecurityRequirement(name = "bearerAuth")
public class CheckController {

    private final CheckService checkService;

    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_BNK.CHEQUES.EMITIR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Emitir cheque", description = "BNK-RF-20: emite cheque fisico o virtual desde chequera activa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cheque emitido correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o regla de negocio incumplida"),
            @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> store(@Valid @RequestBody EmitCheckRequest request, BindingResult bindingResult) {
        return checkService.emit(request, bindingResult);
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_BNK.CHEQUES.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Consultar cheques", description = "BNK-RF-21: consulta paginada de cheques con filtros DataTable.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(mediaType = "application/json", schema = @Schema(implementation = DataTableRequest.class), examples = @ExampleObject(value = "{\n  \"draw\": 1,\n  \"start\": 0,\n  \"length\": 20,\n  \"search\": { \"value\": \"1001\", \"regex\": true },\n  \"columns\": [],\n  \"order\": []\n}"))))
    public ResponseEntity<?> search(@RequestBody(required = false) DataTableRequest request) {
        return checkService.findAllPaged(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_BNK.CHEQUES.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Detalle de cheque", description = "Consulta el detalle de un cheque por id.")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return checkService.getDetail(id);
    }

    /**
     * QA Bloque AU (2026-05-06) — Bug 5: descargar el documento soporte
     * de un cheque virtual. Antes el contenido se guardaba en disco
     * (ephemeral en docker) y no habia endpoint para descargarlo.
     */
    @GetMapping("/{id}/support/download")
    @PreAuthorize("hasAuthority('PERM_BNK.CHEQUES.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Descargar soporte cheque virtual",
            description = "Devuelve el archivo binario (PDF/JPG/PNG) del soporte del cheque virtual.")
    public ResponseEntity<?> downloadSupport(@PathVariable Long id) {
        return checkService.downloadSupport(id);
    }

    @PutMapping("/{id}/void")
    @PreAuthorize("hasAuthority('PERM_VOID_BANK_CHECK') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Anular cheque", description = "BNK-RF-22: anula un cheque con motivo y confirmacion por contrasena.")
    public ResponseEntity<?> voidCheck(
            @PathVariable Long id,
            @Valid @RequestBody VoidCheckRequest request,
            BindingResult bindingResult) {
        return checkService.voidCheck(id, request, bindingResult);
    }

    @PutMapping("/{id}/report-lost")
    @PreAuthorize("hasAuthority('PERM_REPORT_LOST_BANK_CHECK') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Reportar cheque extraviado", description = "BNK-RF-23: reporta incidente y bloquea pago del cheque.")
    public ResponseEntity<?> reportLost(
            @PathVariable Long id,
            @Valid @RequestBody ReportLostCheckRequest request,
            BindingResult bindingResult) {
        return checkService.reportLost(id, request, bindingResult);
    }

    @PutMapping("/{id}/reconcile")
    @PreAuthorize("hasAuthority('PERM_RECONCILE_BANK_CHECK') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Conciliar cheque cobrado", description = "BNK-RF-24: concilia cheque cobrado con metodo manual/automatica/banco.")
    public ResponseEntity<?> reconcile(
            @PathVariable Long id,
            @Valid @RequestBody ReconcileCheckRequest request,
            BindingResult bindingResult) {
        return checkService.reconcile(id, request, bindingResult);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_BANK_CHECK') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(summary = "Eliminar cheque", description = "Eliminacion logica de cheque.")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return checkService.delete(id);
    }
}

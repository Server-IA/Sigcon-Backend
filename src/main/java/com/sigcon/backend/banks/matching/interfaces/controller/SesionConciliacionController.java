package com.sigcon.backend.banks.matching.interfaces.controller;

import com.sigcon.backend.banks.archivos_soporte.domain.model.ArchivoSoporte;
import com.sigcon.backend.banks.matching.application.SesionFase4Requests.*;
import com.sigcon.backend.banks.matching.domain.service.ElectronicSignatureService;
import com.sigcon.backend.banks.matching.domain.service.SesionConciliacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * BNK-HU-066/067/075/077: sesión de conciliación firmada — máquina de estados,
 * firma electrónica, segregación de funciones, reapertura/versionado e informe PDF.
 */
@RestController
@RequestMapping("/api/v1/banks/sesiones-conciliacion")
@RequiredArgsConstructor
@Tag(name = "BNK - Conciliación firmada (HU-066/067/075/077)",
     description = "Cierre y firma de conciliaciones, segregación, reapertura e informe PDF")
public class SesionConciliacionController {

    private final SesionConciliacionService service;
    private final ElectronicSignatureService signatureService;

    private static final String VER = "hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";
    private static final String EDITAR = "hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";

    @Operation(summary = "Crear sesión de conciliación (BORRADOR)")
    @PreAuthorize(EDITAR)
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateSesionRequest req) {
        return ResponseEntity.ok(service.create(req.getBankAccountId(), req.getPeriodStart(), req.getPeriodEnd(), req.getSaldoExtracto()));
    }

    @Operation(summary = "Listar sesiones de la cuenta")
    @PreAuthorize(VER)
    @GetMapping("/cuenta/{bankAccountId}")
    public ResponseEntity<?> list(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.list(bankAccountId));
    }

    @Operation(summary = "Detalle de la sesión")
    @PreAuthorize(VER)
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return ResponseEntity.ok(service.detail(id));
    }

    @Operation(summary = "Libros contables del período de la sesión (Paso 2)")
    @PreAuthorize(VER)
    @GetMapping("/{id}/libros")
    public ResponseEntity<?> libros(@PathVariable Long id) {
        return ResponseEntity.ok(service.librosDelPeriodo(id));
    }

    @Operation(summary = "Extracto bancario importado bajo la sesión (Paso 3)")
    @PreAuthorize(VER)
    @GetMapping("/{id}/extracto")
    public ResponseEntity<?> extracto(@PathVariable Long id) {
        return ResponseEntity.ok(service.extractoDelPeriodo(id));
    }

    @Operation(summary = "Resumen de cierre: conciliación en cero (C1 / Paso 7)")
    @PreAuthorize(VER)
    @GetMapping("/{id}/resumen-cierre")
    public ResponseEntity<?> resumenCierre(@PathVariable Long id) {
        return ResponseEntity.ok(service.resumenCierre(id));
    }

    @Operation(summary = "Firmar (rol=ELABORADOR|REVISOR), 2 pasos OTP (BNK-HU-066 E2/E3)")
    @PreAuthorize(EDITAR)
    @PostMapping("/{id}/firmar")
    public ResponseEntity<?> firmar(@PathVariable Long id,
                                    @RequestParam(defaultValue = "ELABORADOR") String rol,
                                    @RequestBody FirmarRequest req) {
        return ResponseEntity.ok(service.firmar(id, rol, req.getDocumento(), req.getTarjetaProfesional(), req.getMetodo(), req.getOtp()));
    }

    @Operation(summary = "Enviar a revisión (exige firma del elaborador, BNK-HU-066 E2)")
    @PreAuthorize(EDITAR)
    @PostMapping("/{id}/enviar-revision")
    public ResponseEntity<?> sendToReview(@PathVariable Long id) {
        return ResponseEntity.ok(service.sendToReview(id));
    }

    @Operation(summary = "Aprobar (segregación HU-067 E1 + firma revisor HU-066 E3)")
    @PreAuthorize(EDITAR)
    @PostMapping("/{id}/aprobar")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @Operation(summary = "Cerrar + generar informe PDF firmado (HU-067 E3 + HU-077)")
    @PreAuthorize(EDITAR)
    @PostMapping("/{id}/cerrar")
    public ResponseEntity<?> close(@PathVariable Long id) {
        return ResponseEntity.ok(service.close(id));
    }

    @Operation(summary = "Verificar firmas de la sesión (BNK-HU-066 E6)")
    @PreAuthorize(VER)
    @GetMapping("/{id}/verificar-firma")
    public ResponseEntity<?> verify(@PathVariable Long id) {
        return ResponseEntity.ok(service.verificarFirma(id));
    }

    @Operation(summary = "Conciliaciones cerradas activas de la cuenta (Sec 12)")
    @PreAuthorize(VER)
    @GetMapping("/cuenta/{bankAccountId}/cerradas")
    public ResponseEntity<?> cerradas(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.listCerradas(bankAccountId));
    }

    @Operation(summary = "Conciliaciones archivadas de la cuenta (Sec 12, soft-delete a 1 año)")
    @PreAuthorize(VER)
    @GetMapping("/cuenta/{bankAccountId}/archivadas")
    public ResponseEntity<?> archivadas(@PathVariable Long bankAccountId) {
        return ResponseEntity.ok(service.listArchivadas(bankAccountId));
    }

    @Operation(summary = "Archivar conciliación cerrada con más de 1 año (Sec 12)")
    @PreAuthorize(EDITAR)
    @PostMapping("/{id}/archivar")
    public ResponseEntity<?> archivar(@PathVariable Long id) {
        return ResponseEntity.ok(service.archivar(id));
    }

    @Operation(summary = "Histórico de versiones (BNK-HU-075 E8)")
    @PreAuthorize(VER)
    @GetMapping("/{id}/historial")
    public ResponseEntity<?> historial(@PathVariable Long id) {
        return ResponseEntity.ok(service.historial(id));
    }

    @Operation(summary = "Sugerir reverso de asientos de ajuste APROBADOS (BNK-HU-075 E6)")
    @PreAuthorize(VER)
    @GetMapping("/{id}/sugerir-reverso")
    public ResponseEntity<?> sugerirReverso(@PathVariable Long id) {
        return ResponseEntity.ok(service.sugerirReverso(id));
    }

    @Operation(summary = "Solicitudes de reapertura de la sesión")
    @PreAuthorize(VER)
    @GetMapping("/{id}/solicitudes")
    public ResponseEntity<?> solicitudes(@PathVariable Long id) {
        return ResponseEntity.ok(service.solicitudes(id));
    }

    @Operation(summary = "Solicitar reapertura (BNK-HU-075 E1/E2)")
    @PreAuthorize(EDITAR)
    @PostMapping("/{id}/reapertura/solicitar")
    public ResponseEntity<?> solicitarReapertura(@PathVariable Long id, @RequestBody SolicitudReaperturaRequest req) {
        return ResponseEntity.ok(service.solicitarReapertura(id, req.getMotivo(), req.getTipoCambioEsperado(),
                req.getEvidenciaFileName(), req.getEvidenciaHash()));
    }

    @Operation(summary = "Aprobar reapertura (segregación E3 + REABRIR E4 + nueva versión E5)")
    @PreAuthorize(EDITAR)
    @PostMapping("/reaperturas/{solicitudId}/aprobar")
    public ResponseEntity<?> aprobarReapertura(@PathVariable Long solicitudId, @RequestBody AprobarReaperturaRequest req) {
        return ResponseEntity.ok(service.aprobarReapertura(solicitudId, req.getConfirmText(), req.getDocumento(), req.getTarjetaProfesional()));
    }

    @Operation(summary = "Rechazar solicitud de reapertura")
    @PreAuthorize(EDITAR)
    @PostMapping("/reaperturas/{solicitudId}/rechazar")
    public ResponseEntity<?> rechazarReapertura(@PathVariable Long solicitudId, @RequestBody AprobarReaperturaRequest req) {
        return ResponseEntity.ok(service.rechazarReapertura(solicitudId, req.getMotivoRechazo()));
    }

    @Operation(summary = "Descargar informe PDF firmado (BNK-HU-077 E8)")
    @PreAuthorize(VER)
    @GetMapping("/{id}/informe.pdf")
    public ResponseEntity<byte[]> informe(@PathVariable Long id) {
        ArchivoSoporte a = service.downloadInforme(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + a.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(a.getFileContent());
    }

    @Operation(summary = "Consultar configuración de firma (BNK-HU-066 E1)")
    @PreAuthorize(VER)
    @GetMapping("/config-firma")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(signatureService.getOrCreateConfig());
    }

    @Operation(summary = "Actualizar configuración de firma (BNK-HU-066 E1)")
    @PreAuthorize(EDITAR)
    @PutMapping("/config-firma")
    public ResponseEntity<?> updateConfig(@RequestBody ConfigFirmaRequest req) {
        return ResponseEntity.ok(signatureService.updateConfig(req.getMetodosPermitidos(), req.getExigeCertRevisor(), req.getModoFlexible()));
    }
}

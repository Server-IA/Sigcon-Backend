package com.sigcon.backend.audit.interfaces.controller;

import com.sigcon.backend.audit.application.AuditFindingDTO;
import com.sigcon.backend.audit.application.CreateAuditFindingRequest;
import com.sigcon.backend.audit.domain.service.AuditFindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-AU-08 E4 (2026-04-28): Endpoints REST para gestion de hallazgos.
 *
 * <p>Flujo: ABIERTO -> EN_REVISION -> CERRADO. Cada transicion genera un
 * evento de auditoria con trazabilidad completa.
 */
@RestController
@RequestMapping("/api/v1/audit/findings")
@RequiredArgsConstructor
@Tag(name = "Auditoria - Hallazgos",
     description = "Gestion de hallazgos de auditoria con flujo ABIERTO/EN_REVISION/CERRADO (HU-AU-08 E4)")
public class AuditFindingController {

    private final AuditFindingService service;

    @Operation(summary = "Crear hallazgo (ABIERTO)",
               description = "Registra un hallazgo nuevo vinculado a un log de auditoria.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Hallazgo creado"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos o log inexistente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    @PreAuthorize("hasAuthority('PERM_AU.HALLAZGOS.CREAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateAuditFindingRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @Operation(summary = "Listar hallazgos paginados",
               description = "Filtros opcionales por status (ABIERTO/EN_REVISION/CERRADO).")
    @PreAuthorize("hasAuthority('PERM_AU.HALLAZGOS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping
    public ResponseEntity<?> list(
            @Parameter(description = "Pagina (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamano pagina (max 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Filtrar por status") @RequestParam(required = false) String status) {
        Page<AuditFindingDTO> result = service.list(page, size, status);
        Map<String, Object> body = new HashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Detalle de un hallazgo")
    @PreAuthorize("hasAuthority('PERM_AU.HALLAZGOS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @Operation(summary = "Hallazgos vinculados a un log",
               description = "Lista los hallazgos asociados a un audit log especifico.")
    @PreAuthorize("hasAuthority('PERM_AU.HALLAZGOS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping("/by-log/{auditLogId}")
    public ResponseEntity<List<AuditFindingDTO>> byLog(@PathVariable Long auditLogId) {
        return ResponseEntity.ok(service.findByAuditLogId(auditLogId));
    }

    @Operation(summary = "Pasar hallazgo a EN_REVISION",
               description = "Solo desde estado ABIERTO. Asigna el revisor (opcional).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "400", description = "Transicion no permitida")
    })
    @PreAuthorize("hasAuthority('PERM_AU.HALLAZGOS.REVISAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/{id}/start-review")
    public ResponseEntity<?> startReview(@PathVariable Long id,
                                          @RequestBody(required = false) StartReviewRequest req) {
        String reviewer = req != null ? req.getReviewer() : null;
        return ResponseEntity.ok(service.startReview(id, reviewer));
    }

    @Operation(summary = "Cerrar hallazgo",
               description = "Cierra el hallazgo con la resolucion (min 10 caracteres). Inmutable post-cierre.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Hallazgo cerrado"),
        @ApiResponse(responseCode = "400", description = "Resolucion faltante o ya cerrado")
    })
    @PreAuthorize("hasAuthority('PERM_AU.HALLAZGOS.CERRAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/{id}/close")
    public ResponseEntity<?> close(@PathVariable Long id,
                                    @RequestBody CloseRequest req) {
        return ResponseEntity.ok(service.close(id,
                req != null ? req.getResolution() : null));
    }

    @Operation(summary = "Eliminar hallazgo",
               description = "Soft delete. Solo permitido en estado ABIERTO.")
    @PreAuthorize("hasAuthority('PERM_AU.HALLAZGOS.ELIMINAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("success", true,
                "message", "Hallazgo eliminado"));
    }

    @Data
    @Schema(description = "Asignar revisor al pasar a EN_REVISION")
    public static class StartReviewRequest {
        @Schema(description = "Email del revisor (opcional)")
        private String reviewer;
    }

    @Data
    @Schema(description = "Cerrar hallazgo con resolucion")
    public static class CloseRequest {
        @Schema(description = "Resolucion / conclusion (min 10 chars)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String resolution;
    }
}

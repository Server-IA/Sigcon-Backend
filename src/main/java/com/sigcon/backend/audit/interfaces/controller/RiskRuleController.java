package com.sigcon.backend.audit.interfaces.controller;

import com.sigcon.backend.audit.domain.model.AuditRiskRule;
import com.sigcon.backend.audit.domain.service.RiskRuleService;
import com.sigcon.backend.utils.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * HU-AU-04: Reglas configurables de clasificacion por riesgo.
 *
 * <p>El admin puede definir reglas dinamicas (modulo + accion + tipo entidad =>
 * severidad). Las reglas se evaluan en orden de prioridad descendente y la primera
 * que matchea se aplica al evento.
 */
@RestController
@RequestMapping("/api/v1/audit/risk-rules")
@RequiredArgsConstructor
@Tag(name = "Auditoria - Reglas de Riesgo",
     description = "Reglas configurables de clasificacion por severidad (HU-AU-04)")
public class RiskRuleController {

    private final RiskRuleService service;
    private final UserUtil userUtil;

    @Operation(
        summary = "Listar reglas de riesgo (HU-AU-04 E1)",
        description = "Retorna todas las reglas configuradas. Las activas se evaluan en orden "
                    + "de prioridad descendente al clasificar nuevos eventos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado de reglas"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @GetMapping
    public ResponseEntity<?> list() { return service.list(); }

    @Operation(
        summary = "Crear regla de riesgo (HU-AU-04 E1)",
        description = "Crea nueva regla. Campos: name (obligatorio), description, "
                    + "matchModule/matchAction/matchEntityType (null = wildcard), severity, "
                    + "priority (mayor = se evalua primero, default 100), enabled.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Regla creada"),
        @ApiResponse(responseCode = "400", description = "Validacion fallida"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN (HU-AU-04 E4)")
    })
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody AuditRiskRule rule) {
        String createdBy = "admin";
        try { createdBy = userUtil.getUser().getEmail(); } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
     throw __tie;
 } catch (Exception ignored) {}
        return service.create(rule, createdBy);
    }

    @Operation(
        summary = "Actualizar regla de riesgo (HU-AU-04 E3)",
        description = "Actualiza condiciones, severidad o prioridad. Las nuevas reglas aplican "
                    + "a eventos POSTERIORES (no reclasifica eventos ya registrados).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Regla actualizada"),
        @ApiResponse(responseCode = "400", description = "Regla no encontrada"),
        @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "ID de la regla", example = "1") @PathVariable Long id,
            @RequestBody AuditRiskRule rule) {
        return service.update(id, rule);
    }

    @Operation(
        summary = "Activar/desactivar regla",
        description = "Toggle del flag enabled. Las reglas desactivadas no se evaluan.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Regla con nuevo estado"),
        @ApiResponse(responseCode = "400", description = "Regla no encontrada")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @PostMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(
            @Parameter(description = "ID de la regla", example = "1") @PathVariable Long id) {
        return service.toggle(id);
    }

    @Operation(
        summary = "Eliminar regla (soft delete)",
        description = "Marca la regla como eliminada. El historial de eventos clasificados con "
                    + "ella permanece intacto.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Regla eliminada"),
        @ApiResponse(responseCode = "400", description = "Regla no encontrada")
    })
    @PreAuthorize("hasAuthority('PERM_AU.LOG.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "ID de la regla", example = "1") @PathVariable Long id) {
        return service.delete(id);
    }
}

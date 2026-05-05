package com.sigcon.backend.integration.interfaces.controller;

import com.sigcon.backend.integration.domain.service.JwtAuditService;
import com.sigcon.backend.integration.infrastructure.security.AgroFusionJwtValidator;
import com.sigcon.backend.integration.infrastructure.security.JwtConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-INT-RF-11 (operacion): endpoints administrativos para gestionar la
 * configuracion de la integracion AAEF en caliente.
 *
 * <p>Permite al administrador:
 * <ul>
 *   <li>{@code GET /api/contabilidad/admin/jwt-config} - inspeccionar la configuracion JWT activa</li>
 *   <li>{@code POST /api/contabilidad/admin/jwt-config/reload} - invalidar el cache de
 *       {@link JwtConfigService} y forzar re-lectura desde tabla {@code parameters}</li>
 *   <li>{@code POST /api/contabilidad/admin/jwt-config/reload-jwks} - invalidar el cache
 *       remoto del JWKS (clave publica del IdP)</li>
 * </ul>
 *
 * <p><b>Caso de uso:</b> cuando se actualizan parametros como {@code AGROFUSION_JWT_ISSUER},
 * {@code AGROFUSION_JWKS_URL}, {@code AGROFUSION_JWT_SCOPE_REQUIRED} o
 * {@code AGROFUSION_JWT_ENABLED} via UI/SQL, el cache en memoria queda obsoleto.
 * Antes de este endpoint era necesario reiniciar el backend (downtime). Con este
 * endpoint el cambio aplica al siguiente request.
 *
 * <p><b>Seguridad:</b> ROLE_ADMIN obligatorio (no puede llamarlo AgroFusion).
 */
@Slf4j
@RestController
@RequestMapping("/api/contabilidad/admin")
@RequiredArgsConstructor
@Tag(name = "Integracion AAEF - Administracion",
     description = "Endpoints administrativos para gestionar configuracion JWT y JWKS en caliente. "
                 + "Solo ROLE_ADMIN.")
public class IntegrationAdminController {

    private final JwtConfigService jwtConfigService;
    private final AgroFusionJwtValidator jwtValidator;
    private final JwtAuditService jwtAuditService;
    // Spec AAEF Bloque W: scheduler de retencion 5 anios.
    private final com.sigcon.backend.integration.domain.service.IntegrationRetentionScheduler retentionScheduler;

    @Operation(
        summary = "Inspeccionar configuracion JWT actualmente cacheada",
        description = "Retorna los valores que el filtro JWT esta usando ahora mismo "
                    + "(issuer, JWKS URL, scope requerido, enabled). Util para verificar "
                    + "si el cache esta desincronizado de los parametros en BD.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuracion JWT actual"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Falta ROLE_ADMIN")
    })
    @GetMapping(value = "/jwt-config", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> getCurrentConfig() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", jwtConfigService.isEnabled());
        body.put("issuer", jwtConfigService.getIssuer());
        body.put("jwksUrl", jwtConfigService.getJwksUrl());
        body.put("scopeRequired", jwtConfigService.getScopeRequired());
        body.put("inspectedAt", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @Operation(
        summary = "Recargar configuracion JWT desde BD (sin reiniciar backend)",
        description = "Invalida el cache en memoria de JwtConfigService. La proxima vez que "
                    + "el filtro JWT necesite issuer/jwksUrl/scope, los lee de la tabla "
                    + "parameters (categoria INTEGRATION_AGROFUSION). Util tras UPDATE de "
                    + "parametros desde la UI o desde un script SQL en migracion productiva.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cache invalidado, configuracion recargada"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Falta ROLE_ADMIN")
    })
    @PostMapping(value = "/jwt-config/reload", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> reloadJwtConfig() {
        log.info("IntegrationAdminController: reload de configuracion JWT solicitado por admin");
        jwtConfigService.reload();
        // Devolver los nuevos valores leidos para confirmar
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reloaded", true);
        body.put("enabled", jwtConfigService.isEnabled());
        body.put("issuer", jwtConfigService.getIssuer());
        body.put("jwksUrl", jwtConfigService.getJwksUrl());
        body.put("scopeRequired", jwtConfigService.getScopeRequired());
        body.put("reloadedAt", Instant.now().toString());
        log.info("IntegrationAdminController: configuracion recargada -> issuer={}, jwksUrl={}, scope={}, enabled={}",
                body.get("issuer"), body.get("jwksUrl"), body.get("scopeRequired"), body.get("enabled"));
        return ResponseEntity.ok(body);
    }

    @Operation(
        summary = "Invalidar cache de JWKS remoto (forzar refetch)",
        description = "Cuando AgroFusion rota su par de claves RSA, el cache local del JWKS "
                    + "(TTL 5 minutos) puede estar desactualizado. Este endpoint fuerza al "
                    + "validador a refetch el JWKS en el siguiente request, evitando esperar "
                    + "los 5 minutos de TTL natural.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cache JWKS invalidado"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Falta ROLE_ADMIN")
    })
    @PostMapping(value = "/jwt-config/reload-jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> reloadJwks() {
        log.info("IntegrationAdminController: invalidacion de cache JWKS solicitada por admin");
        jwtValidator.invalidateJwksCache();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jwksCacheInvalidated", true);
        body.put("jwksUrl", jwtConfigService.getJwksUrl());
        body.put("note", "El proximo request a /api/contabilidad/** con Bearer token forzara refetch del JWKS");
        body.put("invalidatedAt", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    // ==========================================================================
    // HU-INT-RF-11 (forensia): consulta del log de tokens JWT validados
    // ==========================================================================

    @Operation(
        summary = "Buscar en log forense de tokens JWT validados",
        description = "Lista paginada de tokens validados por AgroFusionJwtFilter. Cada entrada "
                    + "incluye: kid del JWK, subject, claims iat/exp/iss, scope, resultado "
                    + "(VALID/EXPIRED/etc), IP, User-Agent y path. Filtros opcionales: "
                    + "result, subject, kid, rango de fechas. Util para auditoria forense "
                    + "de quien envio que tokens, cuando y desde donde.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado paginado del log forense"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Falta ROLE_ADMIN")
    })
    @GetMapping(value = "/jwt-audit", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> jwtAuditSearch(
            @Parameter(description = "Filtrar por resultado", example = "VALID")
            @RequestParam(required = false) String result,
            @Parameter(description = "Filtrar por subject (LIKE %subject%)", example = "agrofusion")
            @RequestParam(required = false) String subject,
            @Parameter(description = "Filtrar por kid exacto", example = "mock-idp-a1b2c3d4")
            @RequestParam(required = false) String kid,
            @Parameter(description = "Fecha-hora desde (ISO)", example = "2026-04-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Fecha-hora hasta (ISO)", example = "2026-04-30T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Pagina (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanio de pagina", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(jwtAuditService.search(result, subject, kid, from, to, page, size));
    }

    @Operation(
        summary = "Resumen de validaciones JWT por resultado",
        description = "Conteo agregado por categoria (VALID, EXPIRED, INVALID_SCOPE, "
                    + "WRONG_ISSUER, INVALID_SIGNATURE, MALFORMED, JWKS_UNAVAILABLE) + total. "
                    + "Util para dashboard rapido de salud de la integracion JWT.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumen agregado"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Falta ROLE_ADMIN")
    })
    @GetMapping(value = "/jwt-audit/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> jwtAuditSummary() {
        return ResponseEntity.ok(jwtAuditService.summary());
    }

    // ===================================================================
    // Spec AAEF Bloque W: Retencion 5 anios automatica
    // ===================================================================

    @Operation(
        summary = "Forzar purga de retencion AAEF (manual)",
        description = "Ejecuta la purga inmediata de batches/transfers AAEF mas viejos "
                    + "que el cutoff (5 anios por defecto, configurable via parametro "
                    + "AGROFUSION_RETENTION_YEARS). Util para auditorias o limpieza puntual. "
                    + "Tambien corre automaticamente cada noche a las 03:00 AM via "
                    + "IntegrationRetentionScheduler. Solo ROLE_ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Purga ejecutada con conteo de "
                + "transfers/batches eliminados"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Falta ROLE_ADMIN")
    })
    @org.springframework.web.bind.annotation.PostMapping(value = "/retention/run",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    public ResponseEntity<?> runRetention() {
        return ResponseEntity.ok(retentionScheduler.runRetentionManually());
    }
}

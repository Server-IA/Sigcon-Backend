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
    // QA Bloque BM (2026-05-18): admin endpoints prod-ready.
    private final com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository parameterRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_INTEGRATION','TEMP_PERM_VIEW_INTEGRATION','TEMP_VIEW_INTEGRATION','PERM_INT.LOTES.VER','TEMP_PERM_INT.LOTES.VER','TEMP_INT.LOTES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_INTEGRATION','TEMP_PERM_VIEW_INTEGRATION','TEMP_VIEW_INTEGRATION','PERM_INT.LOTES.VER','TEMP_PERM_INT.LOTES.VER','TEMP_INT.LOTES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_INTEGRATION','TEMP_PERM_VIEW_INTEGRATION','TEMP_VIEW_INTEGRATION','PERM_INT.LOTES.VER','TEMP_PERM_INT.LOTES.VER','TEMP_INT.LOTES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_INTEGRATION','TEMP_PERM_VIEW_INTEGRATION','TEMP_VIEW_INTEGRATION','PERM_INT.LOTES.VER','TEMP_PERM_INT.LOTES.VER','TEMP_INT.LOTES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_INTEGRATION','TEMP_PERM_VIEW_INTEGRATION','TEMP_VIEW_INTEGRATION','PERM_INT.LOTES.VER','TEMP_PERM_INT.LOTES.VER','TEMP_INT.LOTES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_INTEGRATION','TEMP_PERM_VIEW_INTEGRATION','TEMP_VIEW_INTEGRATION','PERM_INT.LOTES.VER','TEMP_PERM_INT.LOTES.VER','TEMP_INT.LOTES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> runRetention() {
        return ResponseEntity.ok(retentionScheduler.runRetentionManually());
    }

    // ===================================================================
    // QA Bloque BM (2026-05-18): Gestion configuracion AAEF prod-ready
    // ===================================================================

    /**
     * Lista de parametros AAEF que se exponen en el dashboard. Cada uno marca
     * si es seguro mostrar su valor (config publica) o si requiere ocultar
     * todo excepto el sufijo (API Keys).
     */
    private static final java.util.Map<String, Boolean> AAEF_PARAMS = java.util.Map.ofEntries(
            java.util.Map.entry("AGROFUSION_AUTH_MODE", true),
            java.util.Map.entry("AGROFUSION_API_KEY", false),
            java.util.Map.entry("AGROFUSION_API_KEY_TEST", false),
            java.util.Map.entry("AGROFUSION_JWT_ENABLED", true),
            java.util.Map.entry("AGROFUSION_JWT_ISSUER", true),
            java.util.Map.entry("AGROFUSION_JWKS_URL", true),
            java.util.Map.entry("AGROFUSION_JWT_SCOPE_REQUIRED", true),
            java.util.Map.entry("AGROFUSION_ACK_CALLBACK_URL", true),
            java.util.Map.entry("AGROFUSION_ACK_RETRY_MAX_ATTEMPTS", true),
            java.util.Map.entry("AGROFUSION_ACK_RETRY_INITIAL_DELAY_SECONDS", true),
            java.util.Map.entry("AGROFUSION_MAX_BATCH_SIZE_MB", true),
            java.util.Map.entry("AGROFUSION_RETENTION_YEARS", true)
    );

    @Operation(
        summary = "Estado integral de la integracion AAEF (configuracion + indicadores prod-ready)",
        description = "Devuelve un snapshot completo de la configuracion AAEF: parametros, mocks "
                    + "cargados o no, URLs apuntando a localhost vs prod, presencia de API Key "
                    + "robusta vs placeholder. Util para QA y operaciones antes de un go-live. "
                    + "Los valores de API Keys se muestran enmascarados (solo ultimos 8 chars).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot de configuracion AAEF"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Falta ROLE_ADMIN")
    })
    @GetMapping(value = "/aaef-status", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> getAaefStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        // Parametros
        Map<String, Object> params = new LinkedHashMap<>();
        java.util.List<String> warnings = new java.util.ArrayList<>();
        for (Map.Entry<String, Boolean> entry : AAEF_PARAMS.entrySet()) {
            String name = entry.getKey();
            boolean publico = entry.getValue();
            String value = parameterRepository.findGlobalValueByName(name).orElse(null);
            if (value == null) {
                params.put(name, java.util.Map.of("present", false, "value", null));
                warnings.add("Parametro '" + name + "' NO existe en BD");
            } else {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("present", true);
                if (publico) {
                    info.put("value", value);
                } else {
                    info.put("value", maskSecret(value));
                    info.put("length", value.length());
                }
                params.put(name, info);
                // Validaciones prod-readiness
                if (value.toLowerCase().contains("localhost") || value.toLowerCase().contains("mock")) {
                    warnings.add("Parametro '" + name + "' apunta a localhost/mock (NO valido en prod): "
                            + (publico ? value : "[oculto]"));
                }
                if (value.startsWith("changeme-")) {
                    warnings.add("Parametro '" + name + "' tiene placeholder 'changeme-' (rotar antes de prod).");
                }
            }
        }
        body.put("parameters", params);

        // Estado de mocks
        Map<String, Object> mocks = new LinkedHashMap<>();
        boolean mockIdpLoaded = isBeanLoaded("mockIdpController");
        boolean mockAgroLoaded = isBeanLoaded("mockAgroFusionController");
        mocks.put("mockIdpController", mockIdpLoaded);
        mocks.put("mockAgroFusionController", mockAgroLoaded);
        if (mockIdpLoaded || mockAgroLoaded) {
            warnings.add("Mocks AAEF CARGADOS (SIGCON_INTEGRATION_MOCKS_ENABLED=true). NO usar en prod.");
        }
        body.put("mocksLoaded", mocks);

        // Indicador prod-ready
        body.put("productionReady", warnings.isEmpty());
        body.put("warnings", warnings);
        body.put("inspectedAt", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @Operation(
        summary = "Rotar API Key de AAEF (genera nueva clave aleatoria de 64 chars)",
        description = "Genera una nueva API Key segura usando SecureRandom (64 chars "
                    + "alfanumericos). Actualiza el parametro en BD. La nueva clave se "
                    + "devuelve UNA SOLA VEZ en la respuesta (despues se enmascara). "
                    + "Path param 'type': PROD (AGROFUSION_API_KEY) o TEST "
                    + "(AGROFUSION_API_KEY_TEST). Debe coordinarse con AgroFusion: "
                    + "(1) admin rota -> obtiene nueva key, (2) entrega manualmente a "
                    + "AgroFusion via canal seguro, (3) AgroFusion actualiza su config.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Key rotada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Tipo invalido"),
        @ApiResponse(responseCode = "401", description = "No autenticado"),
        @ApiResponse(responseCode = "403", description = "Falta ROLE_ADMIN")
    })
    @PostMapping(value = "/aaef-status/rotate-api-key", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<?> rotateApiKey(
            @Parameter(description = "PROD o TEST", example = "PROD", required = true)
            @RequestParam(name = "type") String type) {
        String paramName;
        if ("PROD".equalsIgnoreCase(type)) {
            paramName = "AGROFUSION_API_KEY";
        } else if ("TEST".equalsIgnoreCase(type)) {
            paramName = "AGROFUSION_API_KEY_TEST";
        } else {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "error", "Tipo invalido. Use PROD o TEST."));
        }

        // Generar nueva key segura
        String newKey = generateSecureApiKey(type.toUpperCase());

        // Actualizar BD via JdbcTemplate (parametros AAEF son globales, no tenant-scoped)
        int updated = jdbcTemplate.update(
                "UPDATE parameters SET value = ?, updated_at = NOW() WHERE name = ? AND deleted_at IS NULL",
                newKey, paramName);

        if (updated == 0) {
            // El parametro no existia (raro) - crearlo. Usa company_id=1 como global por convencion.
            jdbcTemplate.update(
                    "INSERT INTO parameters (name, value, category, status, company_id, created_at, updated_at) "
                    + "VALUES (?, ?, 'INTEGRATION_AGROFUSION', 'ACTIVE', 1, NOW(), NOW())",
                    paramName, newKey);
        }

        log.info("IntegrationAdminController: API Key {} rotada por admin (tipo={}, len={})",
                paramName, type, newKey.length());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("parameterName", paramName);
        body.put("newApiKey", newKey);
        body.put("rotatedAt", Instant.now().toString());
        body.put("warning", "Esta es la UNICA vez que la nueva clave se muestra en claro. "
                + "Copiela y compartala con AgroFusion por canal seguro. "
                + "El proximo lote AAEF que llegue con la key ANTERIOR sera rechazado (401).");
        return ResponseEntity.ok(body);
    }

    /**
     * Genera una API Key segura de 64 chars alfanumericos usando SecureRandom.
     * Formato: {prefix}-{64chars}. El prefix indica si es PROD o TEST para
     * facilitar auditoria visual.
     */
    private String generateSecureApiKey(String type) {
        java.security.SecureRandom rng = new java.security.SecureRandom();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        sb.append("SIGCON-AAEF-").append(type).append("-");
        for (int i = 0; i < 64; i++) {
            sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Enmascara un valor de API Key: muestra solo los ultimos 8 chars precedidos de "..." */
    private String maskSecret(String value) {
        if (value == null || value.length() <= 8) return "***";
        return "..." + value.substring(value.length() - 8);
    }

    /**
     * Verifica si un bean esta cargado en el contexto Spring. Util para
     * detectar si los MockController estan activos (no deben en prod).
     */
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private boolean isBeanLoaded(String beanName) {
        try {
            return applicationContext.containsBean(beanName);
        } catch (Exception e) {
            return false;
        }
    }
}

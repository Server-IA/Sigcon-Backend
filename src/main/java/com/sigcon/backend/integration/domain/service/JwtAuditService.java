package com.sigcon.backend.integration.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sigcon.backend.integration.application.JwtAuditLogDTO;
import com.sigcon.backend.integration.domain.model.JwtAuditLog;
import com.sigcon.backend.integration.domain.repository.JwtAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-INT-RF-11 (forensia): registra en {@code jwt_audit_log} cada validacion
 * de token JWT realizada por {@code AgroFusionJwtFilter}.
 *
 * <p>Todos los inserts son {@code @Transactional(REQUIRES_NEW)} para que el log
 * quede persistido aunque la transaccion del request principal falle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtAuditService {

    private final JwtAuditLogRepository repository;

    /**
     * Registra una validacion exitosa.
     *
     * @param claims  claims decodificadas del token
     * @param kid     key id del JWK que firmo (puede ser null)
     * @param scope   scope requerido configurado
     * @param request request HTTP para extraer IP/User-Agent/path
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(JsonNode claims, String kid, String scope,
                              HttpServletRequest request) {
        try {
            JwtAuditLog log = baseLog(request)
                    .kid(kid)
                    .subject(claims.has("sub") ? claims.get("sub").asText() : null)
                    .issuer(claims.has("iss") ? claims.get("iss").asText() : null)
                    .issuedAt(toLdt(claims, "iat"))
                    .expiresAt(toLdt(claims, "exp"))
                    .scope(scope)
                    .result("VALID")
                    .build();
            repository.save(log);
        } catch (Exception ex) {
            // El log forense NO debe romper el request. Solo warn.
            JwtAuditService.log.warn("No se pudo registrar JWT audit (success): {}", ex.getMessage());
        }
    }

    /**
     * Registra una validacion fallida.
     *
     * @param errorType  ErrorType.name() (EXPIRED, INVALID_SCOPE, etc.)
     * @param message    mensaje de error
     * @param request    request HTTP
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String errorType, String message, HttpServletRequest request) {
        try {
            JwtAuditLog log = baseLog(request)
                    .result(errorType)
                    .errorMessage(truncate(message, 500))
                    .build();
            repository.save(log);
        } catch (Exception ex) {
            JwtAuditService.log.warn("No se pudo registrar JWT audit (failure): {}", ex.getMessage());
        }
    }

    /**
     * Busqueda paginada con filtros (todos opcionales).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> search(String result, String subject, String kid,
                                       LocalDateTime from, LocalDateTime to,
                                       int page, int size) {
        Page<JwtAuditLog> p = repository.search(
                result, subject, kid, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "validatedAt")));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", p.getContent().stream().map(this::toDTO).toList());
        resp.put("totalElements", p.getTotalElements());
        resp.put("totalPages", p.getTotalPages());
        resp.put("page", page);
        resp.put("size", size);
        return resp;
    }

    /**
     * Resumen rapido por resultado para dashboard admin.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", repository.count());
        resp.put("valid", repository.countByResult("VALID"));
        resp.put("expired", repository.countByResult("EXPIRED"));
        resp.put("invalidScope", repository.countByResult("INVALID_SCOPE"));
        resp.put("wrongIssuer", repository.countByResult("WRONG_ISSUER"));
        resp.put("invalidSignature", repository.countByResult("INVALID_SIGNATURE"));
        resp.put("malformed", repository.countByResult("MALFORMED"));
        resp.put("jwksUnavailable", repository.countByResult("JWKS_UNAVAILABLE"));
        return resp;
    }

    private JwtAuditLog.JwtAuditLogBuilder baseLog(HttpServletRequest request) {
        JwtAuditLog.JwtAuditLogBuilder b = JwtAuditLog.builder()
                .validatedAt(LocalDateTime.now());
        if (request != null) {
            b.remoteIp(getClientIp(request))
             .userAgent(truncate(request.getHeader("User-Agent"), 500))
             .requestPath(request.getRequestURI());
        }
        return b;
    }

    /** Extrae IP real considerando X-Forwarded-For (proxy/loadbalancer). */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // El primer IP del header es el cliente original
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private LocalDateTime toLdt(JsonNode claims, String key) {
        if (!claims.has(key)) return null;
        try {
            long epoch = claims.get(key).asLong();
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private JwtAuditLogDTO toDTO(JwtAuditLog j) {
        return JwtAuditLogDTO.builder()
                .id(j.getId())
                .kid(j.getKid())
                .subject(j.getSubject())
                .issuedAt(j.getIssuedAt())
                .expiresAt(j.getExpiresAt())
                .issuer(j.getIssuer())
                .scope(j.getScope())
                .result(j.getResult())
                .errorMessage(j.getErrorMessage())
                .remoteIp(j.getRemoteIp())
                .userAgent(j.getUserAgent())
                .requestPath(j.getRequestPath())
                .validatedAt(j.getValidatedAt())
                .build();
    }
}

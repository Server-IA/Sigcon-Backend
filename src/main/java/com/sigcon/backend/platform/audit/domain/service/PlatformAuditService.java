package com.sigcon.backend.platform.audit.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.platform.audit.domain.model.PlatformAuditLog;
import com.sigcon.backend.platform.audit.domain.repository.PlatformAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HU-PA-PLAT-08: Servicio centralizado de escritura/lectura del log de
 * auditoria de plataforma.
 *
 * <p>El metodo {@code log(...)} se invoca desde:
 * <ul>
 *   <li>{@link com.sigcon.backend.platform.companies.domain.service.CompanyService}
 *       (HU-PA-PLAT-01 E7, HU-PA-PLAT-03 E5).</li>
 *   <li>{@link com.sigcon.backend.platform.users.domain.service.PlatformUserService}
 *       (HU-PA-PLAT-07 E1, E3, E4).</li>
 *   <li>Cualquier endpoint que requiera trazabilidad cross-tenant.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAuditService {

    private final PlatformAuditLogRepository repository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    private ObjectMapper mapper() {
        return objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Registra una entrada en el log de plataforma. Usa REQUIRES_NEW para que
     * el log persista aun si la transaccion principal falla.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String targetType, String targetId,
                    String targetLabel, Map<String, Object> payload, Long durationMs) {
        try {
            Long actorId = null;
            String actorEmail = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User) {
                User u = (User) auth.getPrincipal();
                actorId = u.getId();
                actorEmail = u.getEmail();
            } else if (auth != null) {
                actorEmail = auth.getName();
                userRepository.findByEmail(actorEmail).ifPresent(u -> {
                    // capturado en closure mediante el setter abajo si fuera mutable;
                    // pero mantenemos email como minimo identificador
                });
            }

            String remoteIp = "unknown";
            String userAgent = "unknown";
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest req = attrs.getRequest();
                    remoteIp = extractIp(req);
                    String ua = req.getHeader("User-Agent");
                    if (ua != null) userAgent = ua.length() > 500 ? ua.substring(0, 500) : ua;
                }
            } catch (IllegalStateException ignored) {
                // contexto async sin request — dejamos defaults
            }

            String payloadJson = null;
            if (payload != null && !payload.isEmpty()) {
                try {
                    payloadJson = mapper().writeValueAsString(payload);
                } catch (JsonProcessingException ex) {
                    log.warn("PlatformAuditService payload serialize failed: {}", ex.getMessage());
                }
            }

            PlatformAuditLog entry = PlatformAuditLog.builder()
                    .occurredAt(LocalDateTime.now())
                    .actorUserId(actorId)
                    .actorEmail(actorEmail)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetLabel(targetLabel)
                    .payloadJson(payloadJson)
                    .remoteIp(remoteIp)
                    .userAgent(userAgent)
                    .durationMs(durationMs)
                    .build();
            repository.save(entry);
        } catch (RuntimeException ex) {
            // No romper el flujo principal por fallo de auditoria
            log.error("PlatformAuditService.log fallo (action={}): {}", action, ex.getMessage(), ex);
        }
    }

    /** Convenience overload sin payload + duration. */
    public void log(String action, String targetType, String targetId, String targetLabel) {
        log(action, targetType, targetId, targetLabel, null, null);
    }

    private String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) return first.length() > 64 ? first.substring(0, 64) : first;
        }
        String addr = req.getRemoteAddr();
        return addr == null ? "unknown" : (addr.length() > 64 ? addr.substring(0, 64) : addr);
    }

    /**
     * HU-PA-PLAT-08 E1: busqueda paginada con filtros opcionales.
     */
    public Page<PlatformAuditLog> search(LocalDateTime from, LocalDateTime to,
                                          String action, String actor,
                                          String targetType, String targetId,
                                          int page, int size) {
        int safeSize = (size <= 0 || size > 200) ? 50 : size;
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize,
                org.springframework.data.domain.Sort.by("occurredAt").descending());
        org.springframework.data.jpa.domain.Specification<PlatformAuditLog> spec =
                (root, q, cb) -> {
                    java.util.List<jakarta.persistence.criteria.Predicate> preds = new java.util.ArrayList<>();
                    if (from != null) preds.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
                    if (to != null) preds.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
                    if (action != null && !action.isBlank()) preds.add(cb.equal(root.get("action"), action));
                    if (actor != null && !actor.isBlank()) preds.add(cb.equal(root.get("actorEmail"), actor));
                    if (targetType != null && !targetType.isBlank()) preds.add(cb.equal(root.get("targetType"), targetType));
                    if (targetId != null && !targetId.isBlank()) preds.add(cb.equal(root.get("targetId"), targetId));
                    return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
                };
        return repository.findAll(spec, pageable);
    }

    /**
     * Helper para construir payload tipado.
     */
    public static Map<String, Object> payload(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}

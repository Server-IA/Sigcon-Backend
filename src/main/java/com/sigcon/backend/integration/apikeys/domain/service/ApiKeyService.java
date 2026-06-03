package com.sigcon.backend.integration.apikeys.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.integration.apikeys.application.ApiKeyDTO;
import com.sigcon.backend.integration.apikeys.application.GeneratedApiKeyDTO;
import com.sigcon.backend.integration.apikeys.domain.model.ApiKey;
import com.sigcon.backend.integration.apikeys.domain.repository.ApiKeyRepository;
import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PA-RF-28 (Pendientes PA, 2026-06-03): ciclo de vida de las API Keys AAEF.
 *
 * <p>Reglas implementadas:
 * <ul>
 *   <li>Generacion con hash SHA-256 (jamas se persiste el texto plano).</li>
 *   <li>Maximo 2 claves ACTIVE por empresa.</li>
 *   <li>Expiracion por defecto a 365 dias.</li>
 *   <li>Revocacion con motivo (validado en el request, &gt;=20 chars).</li>
 *   <li>Listado de metadata (sin hash).</li>
 *   <li>Validacion por hash en el ApiKeyFilter (con actualizacion de last_used_at).</li>
 * </ul>
 *
 * <p>Es ADITIVO al flujo AAEF actual: la clave global legacy de la tabla
 * parameters sigue siendo valida; estas claves se aceptan ADEMAS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private static final int DEFAULT_VALID_DAYS = 365;
    private static final int MAX_ACTIVE_PER_COMPANY = 2;
    private static final String KEY_FAMILY_PREFIX = "SIGCON-AAEF-";
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final CompanyRepository companyRepository;
    private final AuditPublisher auditPublisher;

    // =====================================================================
    // GENERACION
    // =====================================================================

    /**
     * Genera una nueva API Key para la empresa. Devuelve la clave en texto plano
     * UNA SOLA VEZ; solo se persiste su hash SHA-256.
     *
     * @throws IllegalArgumentException si la empresa no existe / no esta ACTIVE
     * @throws IllegalStateException    si la empresa ya tiene el maximo de claves activas
     */
    @Transactional
    public GeneratedApiKeyDTO generate(Long companyId, Long createdByUserId) {
        if (companyId == null) {
            throw new IllegalArgumentException("companyId es obligatorio");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La empresa indicada no existe (companyId=" + companyId + ")"));
        if (company.getStatus() != Company.CompanyStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Solo se pueden emitir API Keys para empresas activas.");
        }

        long active = apiKeyRepository.countByCompanyIdAndStatus(companyId, ApiKey.STATUS_ACTIVE);
        if (active >= MAX_ACTIVE_PER_COMPANY) {
            throw new IllegalStateException(
                    "La empresa ya tiene el maximo de " + MAX_ACTIVE_PER_COMPANY
                  + " API Keys activas. Revoque una antes de generar otra.");
        }

        String publicId = randomToken(8);
        String secret = randomToken(48);
        String plainKey = KEY_FAMILY_PREFIX + publicId + "-" + secret;
        String prefix = KEY_FAMILY_PREFIX + publicId;
        String hash = sha256Hex(plainKey);

        LocalDateTime now = LocalDateTime.now();
        ApiKey saved = apiKeyRepository.save(ApiKey.builder()
                .companyId(companyId)
                .keyHash(hash)
                .prefix(prefix)
                .status(ApiKey.STATUS_ACTIVE)
                .createdAt(now)
                .expiresAt(now.plusDays(DEFAULT_VALID_DAYS))
                .createdBy(createdByUserId)
                .notifiedExpiry(false)
                .build());

        // Auditoria en la bitacora de la empresa propietaria (sin exponer el secreto).
        TenantContext.runAs(companyId, false, () ->
                auditPublisher.publishCreate(AuditModule.INT, "ApiKey", saved.getId(),
                        "API Key AAEF generada (prefix=" + prefix + ", expira=" + saved.getExpiresAt() + ")"));

        return GeneratedApiKeyDTO.builder()
                .key(ApiKeyDTO.from(saved))
                .plainKey(plainKey)
                .build();
    }

    // =====================================================================
    // LISTADO
    // =====================================================================

    /** Lista la metadata de las API Keys de una empresa (sin hash ni secreto). */
    @Transactional(readOnly = true)
    public List<ApiKeyDTO> list(Long companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("companyId es obligatorio");
        }
        return apiKeyRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().map(ApiKeyDTO::from).toList();
    }

    // =====================================================================
    // REVOCACION
    // =====================================================================

    /**
     * Revoca una API Key ACTIVE. El motivo (validado en el request) queda
     * registrado. Solo se pueden revocar claves activas.
     */
    @Transactional
    public ApiKeyDTO revoke(Long id, String reason, Long revokedByUserId) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La API Key no existe (id=" + id + ")"));
        if (!ApiKey.STATUS_ACTIVE.equals(key.getStatus())) {
            throw new IllegalStateException(
                    "Solo se pueden revocar API Keys activas. Estado actual: " + key.getStatus());
        }
        key.setStatus(ApiKey.STATUS_REVOKED);
        key.setRevokedAt(LocalDateTime.now());
        key.setRevocationReason(reason);
        ApiKey saved = apiKeyRepository.save(key);

        TenantContext.runAs(key.getCompanyId(), false, () ->
                auditPublisher.publishDelete(AuditModule.INT, "ApiKey", saved.getId(),
                        "API Key AAEF revocada (prefix=" + saved.getPrefix()
                      + ", motivo=" + reason + ", por userId=" + revokedByUserId + ")"));
        return ApiKeyDTO.from(saved);
    }

    // =====================================================================
    // VALIDACION (la usa el ApiKeyFilter)
    // =====================================================================

    /**
     * PA-RF-28: valida una clave entrante contra las claves gestionadas (por su
     * hash SHA-256) y, si es ACTIVE y vigente, actualiza {@code last_used_at}.
     * Si esta vencida la marca EXPIRED. Defensivo: nunca lanza (devuelve false
     * ante cualquier error) para no romper el request AAEF.
     *
     * @return true si la clave corresponde a una API Key gestionada ACTIVE y vigente
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean validateAndTouch(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return false;
        try {
            String hash = sha256Hex(rawKey.trim());
            Optional<ApiKey> opt = apiKeyRepository.findByKeyHashAndStatus(hash, ApiKey.STATUS_ACTIVE);
            if (opt.isEmpty()) return false;
            ApiKey key = opt.get();
            LocalDateTime now = LocalDateTime.now();
            if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(now)) {
                // Vencida: marcarla EXPIRED y rechazar.
                key.setStatus(ApiKey.STATUS_EXPIRED);
                apiKeyRepository.save(key);
                return false;
            }
            key.setLastUsedAt(now);
            apiKeyRepository.save(key);
            return true;
        } catch (RuntimeException ex) {
            log.warn("PA-RF-28: error validando API Key gestionada: {}", ex.getMessage());
            return false;
        }
    }

    // =====================================================================
    // MANTENIMIENTO (la usa el scheduler)
    // =====================================================================

    /** Marca como EXPIRED las claves ACTIVE cuya expiracion ya paso. Devuelve cuantas. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markExpired() {
        List<ApiKey> expired = apiKeyRepository
                .findByStatusAndExpiresAtBefore(ApiKey.STATUS_ACTIVE, LocalDateTime.now());
        for (ApiKey k : expired) {
            k.setStatus(ApiKey.STATUS_EXPIRED);
        }
        if (!expired.isEmpty()) {
            apiKeyRepository.saveAll(expired);
            log.info("PA-RF-28: {} API Keys marcadas EXPIRED", expired.size());
        }
        return expired.size();
    }

    /**
     * Avisa (log WARNING) por cada clave ACTIVE que expira dentro de los proximos
     * {@code days} dias y aun no fue avisada; marca notified_expiry=true.
     *
     * <p>Nota: el RF pide notificar al PLATFORM_ADMIN. El sistema de notificaciones
     * in-app es tenant-scoped (companyId NOT NULL) y el PLATFORM_ADMIN no tiene
     * empresa, por lo que aqui el aviso se emite a nivel de log (visible para
     * operaciones de plataforma). Una notificacion in-app de plataforma requeriria
     * un canal cross-tenant que hoy no existe.
     *
     * @return cuantas claves se avisaron
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int notifyUpcomingExpiry(int days) {
        LocalDateTime now = LocalDateTime.now();
        List<ApiKey> soon = apiKeyRepository.findByStatusAndNotifiedExpiryFalseAndExpiresAtBetween(
                ApiKey.STATUS_ACTIVE, now, now.plusDays(days));
        for (ApiKey k : soon) {
            log.warn("PA-RF-28: API Key {} (companyId={}) expira el {} (en <= {} dias). "
                   + "Coordine la rotacion con AgroFusion.",
                    k.getPrefix(), k.getCompanyId(), k.getExpiresAt(), days);
            k.setNotifiedExpiry(true);
        }
        if (!soon.isEmpty()) {
            apiKeyRepository.saveAll(soon);
        }
        return soon.size();
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** SHA-256 en hex de la clave completa. */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular SHA-256", e);
        }
    }
}

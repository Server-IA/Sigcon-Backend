package com.sigcon.backend.integration.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HU-INT-RF-11: Validador de tokens JWT RS256 emitidos por el SSO de AgroFusion.
 *
 * <p>Valida en este orden (y lanza {@link JwtValidationException} con el codigo
 * correspondiente si alguna validacion falla):
 * <ol>
 *   <li>Formato JWT (3 segmentos)</li>
 *   <li>Firma RSA contra JWK publico del JWKS remoto (E5 - firma invalida)</li>
 *   <li>Claim {@code iss} coincide con AGROFUSION_JWT_ISSUER (E4 - issuer incorrecto)</li>
 *   <li>Claim {@code exp} no vencido (E2 - token expirado)</li>
 *   <li>Claim {@code scope} contiene AGROFUSION_JWT_SCOPE_REQUIRED (E3 - scope insuficiente)</li>
 * </ol>
 *
 * <p>El JWKS se cachea 5 minutos para evitar rate-limit. Una rotacion de claves
 * del IdP tomara hasta 5 minutos en propagarse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgroFusionJwtValidator {

    public enum ErrorType {
        EXPIRED,        // 401 - E2
        INVALID_SCOPE,  // 403 - E3
        WRONG_ISSUER,   // 401 - E4
        INVALID_SIGNATURE, // 401 - E5
        MALFORMED,      // 401 - formato
        JWKS_UNAVAILABLE // 500 - IdP caido
    }

    public static class JwtValidationException extends RuntimeException {
        private final ErrorType type;
        public JwtValidationException(ErrorType type, String message) {
            super(message);
            this.type = type;
        }
        public ErrorType getType() { return type; }
    }

    /**
     * Resultado de una validacion exitosa: claims + kid del JWK que firmo.
     * El kid es necesario para auditoria forense (HU-INT-RF-11) - permite saber
     * que clave del IdP firmo el token y detectar uso de claves rotadas.
     */
    public static class ValidationResult {
        private final JsonNode claims;
        private final String kid;
        public ValidationResult(JsonNode claims, String kid) {
            this.claims = claims;
            this.kid = kid;
        }
        public JsonNode getClaims() { return claims; }
        public String getKid() { return kid; }
    }

    private final JwtConfigService config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private final AtomicReference<JWKSet> cachedJwks = new AtomicReference<>();
    private volatile long jwksCachedAt = 0;
    private static final long JWKS_CACHE_TTL_MS = 5 * 60 * 1000L; // 5 min

    /**
     * Valida el token y retorna las claims si es valido. Lanza
     * {@link JwtValidationException} si no pasa alguna regla.
     *
     * <p>Wrapper que mantiene compatibilidad. Usar {@link #validateDetailed(String)}
     * si se necesita acceso al kid (auditoria forense).
     */
    public JsonNode validate(String token) {
        return validateDetailed(token).getClaims();
    }

    /**
     * Variante de {@link #validate(String)} que retorna las claims + el kid
     * del JWK que firmo el token. Util para auditoria forense (HU-INT-RF-11).
     */
    public ValidationResult validateDetailed(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException(ErrorType.MALFORMED, "Token vacio");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtValidationException(ErrorType.MALFORMED, "Token JWT con formato invalido");
        }

        // Parsear claims sin validar firma para leer issuer primero
        JsonNode claims = decodeClaims(parts[1]);

        // Validacion de firma con JWKS del IdP declarado
        String kid = verifySignature(token);

        // Validar issuer (E4)
        String expectedIssuer = config.getIssuer();
        String tokenIssuer = claims.has("iss") ? claims.get("iss").asText() : null;
        if (expectedIssuer == null || !expectedIssuer.equals(tokenIssuer)) {
            throw new JwtValidationException(ErrorType.WRONG_ISSUER, "Issuer no reconocido");
        }

        // Validar exp (E2)
        if (!claims.has("exp")) {
            throw new JwtValidationException(ErrorType.MALFORMED, "Token sin claim exp");
        }
        long exp = claims.get("exp").asLong();
        if (Instant.now().getEpochSecond() >= exp) {
            throw new JwtValidationException(ErrorType.EXPIRED, "Token JWT expirado");
        }

        // Validar scope (E3) - soporta tanto claim "scope" (string con espacios)
        // como "scp" (array), que son las dos convenciones comunes en OAuth2
        String required = config.getScopeRequired();
        Set<String> tokenScopes = extractScopes(claims);
        if (required != null && !tokenScopes.contains(required)) {
            throw new JwtValidationException(
                    ErrorType.INVALID_SCOPE,
                    "Scope insuficiente: se requiere " + required);
        }

        return new ValidationResult(claims, kid);
    }

    // ──────────────────────────────────────────────────────────────

    private JsonNode decodeClaims(String payloadB64) {
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(payloadB64);
            return objectMapper.readTree(decoded);
        } catch (Exception e) {
            throw new JwtValidationException(ErrorType.MALFORMED, "No se pudo decodificar payload JWT");
        }
    }

    /** @return el kid del JWK que firmo el token (o null si la firma uso fallback) */
    private String verifySignature(String token) {
        try {
            JWSObject jws = JWSObject.parse(token);
            String kid = jws.getHeader().getKeyID();
            JWKSet jwks = loadJwks();
            JWK jwk = kid != null ? jwks.getKeyByKeyId(kid)
                    : (jwks.getKeys().isEmpty() ? null : jwks.getKeys().get(0));
            if (jwk == null || !(jwk instanceof RSAKey)) {
                throw new JwtValidationException(ErrorType.INVALID_SIGNATURE,
                        "No se encontro JWK valido para kid=" + kid);
            }
            RSAKey rsa = (RSAKey) jwk;
            RSASSAVerifier verifier = new RSASSAVerifier(rsa.toRSAPublicKey());
            if (!jws.verify(verifier)) {
                throw new JwtValidationException(ErrorType.INVALID_SIGNATURE, "Firma JWT invalida");
            }
            return kid;
        } catch (JwtValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtValidationException(ErrorType.INVALID_SIGNATURE,
                    "Firma JWT invalida: " + e.getMessage());
        }
    }

    private JWKSet loadJwks() {
        long now = System.currentTimeMillis();
        JWKSet cached = cachedJwks.get();
        if (cached != null && (now - jwksCachedAt) < JWKS_CACHE_TTL_MS) {
            return cached;
        }
        String url = config.getJwksUrl();
        if (url == null) {
            throw new JwtValidationException(ErrorType.JWKS_UNAVAILABLE,
                    "JWKS URL no configurada (AGROFUSION_JWKS_URL)");
        }
        try {
            String body = restTemplate.getForObject(url, String.class);
            JWKSet fresh = JWKSet.parse(body);
            cachedJwks.set(fresh);
            jwksCachedAt = now;
            log.info("JWKS cargado desde {} (keys: {})", url, fresh.getKeys().size());
            return fresh;
        } catch (Exception e) {
            throw new JwtValidationException(ErrorType.JWKS_UNAVAILABLE,
                    "No se pudo cargar JWKS: " + e.getMessage());
        }
    }

    private Set<String> extractScopes(JsonNode claims) {
        Set<String> scopes = new HashSet<>();
        if (claims.has("scope") && claims.get("scope").isTextual()) {
            scopes.addAll(Arrays.asList(claims.get("scope").asText().split("\\s+")));
        }
        if (claims.has("scp") && claims.get("scp").isArray()) {
            claims.get("scp").forEach(n -> scopes.add(n.asText()));
        }
        if (claims.has("scopes") && claims.get("scopes").isArray()) {
            claims.get("scopes").forEach(n -> scopes.add(n.asText()));
        }
        return scopes;
    }

    /** Fuerza recarga del JWKS (util para tests y rotacion manual de claves). */
    public void invalidateJwksCache() {
        cachedJwks.set(null);
        jwksCachedAt = 0;
    }
}

package com.sigcon.backend.integration.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HU-INT-RF-11: Mock IdP embebido en SIGCON para smoke tests y entornos de
 * desarrollo donde no existe un SSO de AgroFusion real.
 *
 * <p>Genera un par de claves RSA al arranque y expone:
 * <ul>
 *   <li>{@code GET /mock-idp/.well-known/jwks.json} - claves publicas en formato JWKS</li>
 *   <li>{@code POST /mock-idp/token} - emite JWT firmado (parametros de prueba configurables)</li>
 * </ul>
 *
 * <p><b>IMPORTANTE:</b> este controller esta pensado SOLO para desarrollo/pruebas.
 * En produccion, el parametro {@code AGROFUSION_JWKS_URL} debe apuntar al
 * JWKS real del IdP ({@code https://sso.agrofusion.co/.well-known/jwks.json}) y
 * este endpoint debe deshabilitarse o quedar fuera del ingress publico.
 *
 * <p>Acceso publico (sin auth) porque JWKS siempre es publico por disenio.
 *
 * <p><b>Profile guard:</b> esta clase solo se carga si Spring esta corriendo con
 * el perfil {@code dev} (controlado via {@code SPRING_PROFILES_ACTIVE}).
 * En produccion ({@code SPRING_PROFILES_ACTIVE=PRODUCTION}) Spring NO instancia
 * el bean ni expone los endpoints {@code /mock-idp/**}, eliminando el riesgo de
 * generar tokens en un entorno real.
 */
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "sigcon.integration.mocks-enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/mock-idp")
@RequiredArgsConstructor
@Tag(name = "Mock IdP (solo desarrollo)",
     description = "IdP embebido para smoke test HU-INT-RF-11. NO usar en produccion. "
                 + "Solo activo con la property sigcon.integration.mocks-enabled=true.")
public class MockIdpController {

    private final ObjectMapper objectMapper;

    @Value("${sigcon.mock-idp.issuer:http://localhost:8080/mock-idp}")
    private String mockIssuer;

    private RSAKey rsaJwk;
    private RSASSASigner signer;

    @PostConstruct
    void init() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        var keyPair = gen.generateKeyPair();
        rsaJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("mock-idp-" + UUID.randomUUID().toString().substring(0, 8))
                .build();
        signer = new RSASSASigner(rsaJwk);
        log.info("MockIdpController: par RSA generado (kid={}). Issuer={}",
                rsaJwk.getKeyID(), mockIssuer);
    }

    @Operation(
        summary = "JWKS publico del mock IdP",
        description = "Retorna las claves publicas en formato JWKS para que el validador "
                    + "de SIGCON pueda verificar firmas. Solo claves publicas (no privadas).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "JWKS con 1 clave RSA (RS256)")
    })
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() throws Exception {
        JWKSet set = new JWKSet(rsaJwk.toPublicJWK());
        return ResponseEntity.ok(set.toString());
    }

    @Operation(
        summary = "Emitir token JWT firmado (solo para smoke tests)",
        description = "Genera un JWT RS256 con los parametros especificados. Por defecto "
                    + "genera un token VALIDO con scope aaef:lote:enviar y expiracion +1h. "
                    + "Permite forzar escenarios invalidos: expired=true, scope='otro', "
                    + "issuer='otro' (HU-INT-RF-11 E2, E3, E4).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token JWT firmado (campo 'token')"),
        @ApiResponse(responseCode = "500", description = "Error firmando token")
    })
    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> token(
            @Parameter(description = "Subject del token (sub)", example = "agrofusion")
            @RequestParam(defaultValue = "agrofusion") String subject,
            @Parameter(description = "Scope en el claim 'scope' (default: aaef:lote:enviar)")
            @RequestParam(defaultValue = "aaef:lote:enviar") String scope,
            @Parameter(description = "Override del issuer para simular E4 (default: mock issuer)")
            @RequestParam(required = false) String issuer,
            @Parameter(description = "Si true, emite token ya expirado (simula E2)", example = "false")
            @RequestParam(defaultValue = "false") boolean expired,
            @Parameter(description = "Expiracion en segundos (si expired=false, default 3600)",
                       example = "3600")
            @RequestParam(defaultValue = "3600") int expiresInSeconds) throws JOSEException {

        Instant now = Instant.now();
        Instant exp = expired ? now.minusSeconds(60) : now.plusSeconds(expiresInSeconds);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer != null ? issuer : mockIssuer)
                .subject(subject)
                .claim("scope", scope)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build(),
                claims);
        jwt.sign(signer);

        Map<String, Object> body = new HashMap<>();
        body.put("token", jwt.serialize());
        body.put("issuer", claims.getIssuer());
        body.put("scope", scope);
        body.put("expiresAt", exp.toString());
        body.put("usage", "curl -H \"Authorization: Bearer <token>\" http://localhost:8080/api/contabilidad/...");
        return ResponseEntity.ok(body);
    }
}

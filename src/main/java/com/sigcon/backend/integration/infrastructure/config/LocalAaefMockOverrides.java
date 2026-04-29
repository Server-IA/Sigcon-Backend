package com.sigcon.backend.integration.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * QA-BLOQUE-AL (2026-04-29): override de los parametros AAEF a URLs locales
 * (mock) SOLO cuando se cumple:
 * <ul>
 *   <li>perfil Spring activo {@code dev} (via {@code SPRING_PROFILES_ACTIVE=dev}).
 *   <li>variable {@code sigcon.integration.mocks-enabled=true} (via env
 *       {@code SIGCON_INTEGRATION_MOCKS_ENABLED=true}).
 * </ul>
 *
 * <p>Razon: las migraciones SQL siembran URLs de produccion (V32:
 * {@code https://api.agrofusion.co/...}, {@code https://sso.agrofusion.co/...}).
 * Esos valores son CORRECTOS para Dokploy. En desarrollo local, sin embargo,
 * queremos que el ACK callback y la validacion JWT apunten a los mocks
 * embebidos del propio backend para validar el flujo end-to-end sin depender
 * de servicios externos.
 *
 * <p>Antes este override vivia en V9-9 como un {@code UPDATE} incondicional
 * al callback, y eso rompia produccion despues de cada cold-start (los lotes
 * AAEF intentaban POST a {@code http://localhost:8080/mock-agrofusion/...}
 * desde el contenedor Dokploy, pero los mock controllers estan tras
 * {@code @ConditionalOnProperty(mocks-enabled)} que NO se setea en prod ->
 * 404 -> ACK_FAILED en cada lote).
 *
 * <p>Esta clase corre tras {@code ApplicationReadyEvent} (despues de
 * {@code DataInitializer.executeScripts()}) y aplica los overrides via
 * {@link JdbcTemplate} para no depender del repositorio JPA filtrado por
 * tenant (los parametros AAEF son globales).
 *
 * <p>Idempotente: si los valores ya apuntan al mock local, los UPDATE no
 * cambian nada. Defensivo: si alguien arranca con perfil dev pero sin la
 * variable de mocks, no se aplica el override (las URLs prod permanecen).
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "sigcon.integration.mocks-enabled", havingValue = "true")
@RequiredArgsConstructor
public class LocalAaefMockOverrides {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Host local visible desde el propio contenedor backend. En docker-compose
     * standalone el backend se llama a si mismo via {@code localhost}; si en
     * algun setup el backend corre en otra red, ajustar via env var
     * {@code SIGCON_INTEGRATION_LOCAL_HOST}.
     */
    @Value("${sigcon.integration.local-host:http://localhost:8080}")
    private String localHost;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void overrideToLocalMocks() {
        log.info("LocalAaefMockOverrides: aplicando URLs mock locales (perfil dev + mocks-enabled=true). Base host = {}", localHost);

        String mockAck    = localHost + "/mock-agrofusion/aaef/ack";
        String mockIssuer = localHost + "/mock-idp";
        String mockJwks   = localHost + "/mock-idp/.well-known/jwks.json";

        applyOverride("AGROFUSION_ACK_CALLBACK_URL", mockAck);
        applyOverride("AGROFUSION_JWT_ISSUER", mockIssuer);
        applyOverride("AGROFUSION_JWKS_URL", mockJwks);
    }

    /**
     * Aplica el UPDATE SOLO si el valor actual difiere del esperado mock.
     * Asi el log queda limpio en re-arranques.
     */
    private void applyOverride(String paramName, String mockValue) {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM parameters WHERE name = ? AND deleted_at IS NULL AND value <> ?",
                Integer.class, paramName, mockValue);
        if (rows == null || rows == 0) {
            log.debug("LocalAaefMockOverrides: {} ya apunta al mock, sin cambios.", paramName);
            return;
        }
        int updated = jdbcTemplate.update(
                "UPDATE parameters SET value = ?, updated_at = NOW() WHERE name = ? AND deleted_at IS NULL",
                mockValue, paramName);
        log.info("LocalAaefMockOverrides: {} -> {} ({} fila(s) actualizada(s)).", paramName, mockValue, updated);
    }
}

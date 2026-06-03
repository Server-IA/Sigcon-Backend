package com.sigcon.backend.integration.apikeys.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PA-RF-28 (Pendientes PA, 2026-06-03) punto 4: job de expiracion de API Keys.
 *
 * <p>Por default corre todos los dias a las 03:45 AM. Configurable via
 * {@code sigcon.integration.apikey.expiry-cron}.
 *
 * <p>Hace dos cosas:
 * <ol>
 *   <li>Marca como EXPIRED las claves ACTIVE cuya expiracion ya paso.</li>
 *   <li>Avisa (log WARNING) por cada clave ACTIVE que expira dentro de los
 *       proximos {@value #WARN_DAYS} dias (una sola vez por clave).</li>
 * </ol>
 *
 * <p>El umbral de aviso (30 dias) es configurable via
 * {@code sigcon.integration.apikey.warn-days}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyExpiryScheduler {

    /** Umbral por defecto de aviso de proxima expiracion (dias). */
    public static final int WARN_DAYS = 30;

    @org.springframework.beans.factory.annotation.Value("${sigcon.integration.apikey.warn-days:30}")
    private int warnDays;

    private final ApiKeyService apiKeyService;

    @Scheduled(cron = "${sigcon.integration.apikey.expiry-cron:0 45 3 * * *}")
    public void runScheduled() {
        runNow();
    }

    /**
     * Ejecuta el mantenimiento. Publico para poder dispararlo desde un smoke
     * test o un endpoint admin. Nunca propaga excepciones.
     */
    public void runNow() {
        try {
            int expired = apiKeyService.markExpired();
            int warned = apiKeyService.notifyUpcomingExpiry(warnDays);
            if (expired > 0 || warned > 0) {
                log.info("PA-RF-28 apikey expiry: {} expiradas, {} avisadas (<= {} dias)",
                        expired, warned, warnDays);
            }
        } catch (RuntimeException ex) {
            log.error("PA-RF-28 apikey expiry scheduler failed: {}", ex.getMessage(), ex);
        }
    }
}

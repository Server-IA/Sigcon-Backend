package com.sigcon.backend.parametrization.users.domain.service;

import com.sigcon.backend.parametrization.users.domain.repository.BlackListedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * PA-RF-27 (Pendientes PA, 2026-06-03) punto 4: job de limpieza de la blacklist.
 *
 * <p>El logout inserta cada token invalidado en {@code blacklisted_tokens} con
 * su {@code expires_at} (claim exp del JWT). Sin este job la tabla crece
 * indefinidamente, ya que el {@code BlackListFilter} solo agrega filas y nunca
 * las borra. Un token ya vencido no necesita estar en la lista negra (de todas
 * formas el filtro de expiracion del JWT lo rechazaria), asi que es seguro
 * eliminarlo.
 *
 * <p>Por default corre todos los dias a las 03:15 AM. Configurable via
 * {@code sigcon.parametrization.blacklist.cleanup-cron}.
 *
 * <p>Solo borra filas con {@code expires_at} no nulo (las creadas antes de
 * PA-RF-27 no lo tienen y se conservan; son inocuas y de cardinalidad acotada).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlacklistCleanupScheduler {

    private final BlackListedTokenRepository blackListedTokenRepository;

    @Scheduled(cron = "${sigcon.parametrization.blacklist.cleanup-cron:0 15 3 * * *}")
    public void runScheduled() {
        runNow();
    }

    /**
     * Borra las entradas vencidas. Expuesto como metodo publico para poder
     * dispararlo manualmente desde un endpoint admin o en un smoke test.
     *
     * @return numero de filas eliminadas
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int runNow() {
        long t0 = System.currentTimeMillis();
        int deleted = 0;
        try {
            deleted = blackListedTokenRepository.deleteExpired(LocalDateTime.now());
            if (deleted > 0) {
                log.info("PA-RF-27 blacklist cleanup: {} tokens vencidos eliminados en {}ms",
                        deleted, System.currentTimeMillis() - t0);
            }
        } catch (RuntimeException ex) {
            log.error("PA-RF-27 blacklist cleanup failed: {}", ex.getMessage(), ex);
        }
        return deleted;
    }
}

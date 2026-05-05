package com.sigcon.backend.parametrization.notifications.domain.service;

import com.sigcon.backend.parametrization.notifications.application.NotificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * HU-PA-21 (push opcional): hub in-memory de SseEmitter por usuario.
 *
 * <p>Alternativa al polling clasico. El cliente abre una conexion SSE en
 * {@code GET /api/parametrization/notifications/stream}; el hub mantiene
 * el emitter en memoria y le envia eventos {@code notification} cada vez
 * que se publica una notif para ese user_id.
 *
 * <p>Multiples conexiones por usuario soportadas (diferentes pestanas).
 * Conexiones muertas se purgan automaticamente al primer fallo de envio.
 *
 * <p>Limitaciones (no bloqueantes para HU-PA-21 que se cumple con polling):
 * <ul>
 *   <li>Estado in-memory: en cluster multi-nodo se requiere sticky session
 *       o broadcast via Redis pub/sub.</li>
 *   <li>El usuario que polea con campanita {@link NotificationBell} tiene
 *       cobertura cada 30s; el push es solo upgrade UX.</li>
 * </ul>
 */
@Component
@Slf4j
public class NotificationPushHub {

    private final Map<Long, ConcurrentLinkedQueue<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /** Registra un nuevo emitter para el usuario. Devuelve el emitter listo para usar. */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(0L); // sin timeout (Tomcat impone su default)
        emittersByUser.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("ping").data("ok"));
        } catch (IOException ignored) {}
        log.debug("[SSE] registered user={}", userId);
        return emitter;
    }

    /**
     * HU-PA-21: enviar la notificacion al cliente. Llamado por NotificationService
     * tras persistir una notif. Si no hay conexion, no-op (el cliente eventualmente
     * la vera por polling o al refrescar).
     */
    public void push(Long userId, NotificationDTO dto) {
        ConcurrentLinkedQueue<SseEmitter> queue = emittersByUser.get(userId);
        if (queue == null || queue.isEmpty()) return;
        for (SseEmitter e : queue) {
            try {
                e.send(SseEmitter.event().name("notification").data(dto));
            } catch (IOException ex) {
                remove(userId, e);
            } catch (RuntimeException ex) {
                remove(userId, e);
            }
        }
    }

    /** Heartbeat cada 25s para mantener viva la conexion tras proxies/load balancers. */
    @Scheduled(fixedDelay = 25000)
    public void heartbeat() {
        emittersByUser.forEach((uid, q) -> q.forEach(e -> {
            try { e.send(SseEmitter.event().name("ping").data("hb")); }
            catch (IOException ex) { remove(uid, e); }
            catch (RuntimeException ex) { remove(uid, e); }
        }));
    }

    private void remove(Long userId, SseEmitter emitter) {
        ConcurrentLinkedQueue<SseEmitter> q = emittersByUser.get(userId);
        if (q != null) q.remove(emitter);
    }

    public int activeConnections() {
        return emittersByUser.values().stream().mapToInt(java.util.Queue::size).sum();
    }
}

package com.sigcon.backend.parametrization.notifications.interfaces.controller;

import com.sigcon.backend.parametrization.notifications.application.NotificationDTO;
import com.sigcon.backend.parametrization.notifications.domain.service.NotificationPushHub;
import com.sigcon.backend.parametrization.notifications.domain.service.NotificationService;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HU-PA-21/22/23: bandeja de notificaciones del usuario autenticado.
 * Cualquier usuario logueado accede SOLO a sus propias notificaciones.
 */
@RestController
@RequestMapping("/api/parametrization/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones (Sprint 4)", description = "HU-PA-21..25 bandeja in-app del usuario")
public class NotificationController {

    private final NotificationService service;
    private final UserRepository userRepository;
    private final NotificationPushHub pushHub;

    /**
     * HU-PA-21: bandeja paginada del usuario actual.
     * Filtros: ?module=CG&unreadOnly=true&page=0&size=20
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HU-PA-21: bandeja paginada (orden cronologico desc, no incluye expiradas)")
    public ResponseEntity<?> my(@RequestParam(required = false) String module,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) Boolean unreadOnly,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        User u = currentUser();
        if (u == null) return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
        // QA Bloque PA Bug 53 (HU-PA-21 E2, 2026-05-09): combinar filtros (no excluyentes)
        Page<NotificationDTO> p = service.listForUser(u.getId(), module, type, unreadOnly, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", p.getContent());
        body.put("totalElements", p.getTotalElements());
        body.put("totalPages", p.getTotalPages());
        body.put("page", page);
        body.put("size", size);
        return ResponseEntity.ok(body);
    }

    /** HU-PA-22: contador para el badge de la campanita. */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HU-PA-22: numero de no leidas del usuario actual")
    public ResponseEntity<?> unreadCount() {
        User u = currentUser();
        if (u == null) return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
        return ResponseEntity.ok(Map.of("unreadCount", service.countUnread(u.getId())));
    }

    /** HU-PA-22: marca una notificacion como leida (idempotente). */
    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HU-PA-22: marcar como leida (solo el dueno)")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        User u = currentUser();
        if (u == null) return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
        boolean ok = service.markAsRead(id, u.getId());
        if (!ok) return ResponseEntity.status(404).body(Map.of("success", false,
                "message", "Notificacion no encontrada"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** HU-PA-22: marca todas como leidas. */
    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HU-PA-22: marcar todas como leidas")
    public ResponseEntity<?> markAllRead() {
        User u = currentUser();
        if (u == null) return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
        int updated = service.markAllAsRead(u.getId());
        return ResponseEntity.ok(Map.of("success", true, "updated", updated));
    }

    /** HU-PA-23: navegar al detalle (marca leida y devuelve el action_url). */
    @PostMapping("/{id}/click")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HU-PA-23: registra click + marca leida + devuelve action_url para navegar")
    public ResponseEntity<?> click(@PathVariable Long id) {
        User u = currentUser();
        if (u == null) return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
        Optional<NotificationDTO> opt = service.getAndMarkRead(id, u.getId());
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false,
                "message", "Notificacion no encontrada"));
        return ResponseEntity.ok(opt.get());
    }

    /**
     * HU-PA-21 push opcional: stream SSE. Cliente abre EventSource y recibe
     * eventos {@code notification} cada vez que se publica una notif para el.
     * Si no hay clientes conectados, no hay overhead.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HU-PA-21 push: stream SSE de notificaciones")
    public SseEmitter stream() {
        User u = currentUser();
        if (u == null) throw new RuntimeException("No autenticado");
        return pushHub.register(u.getId());
    }

    private User currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        String username = a.getName();
        return userRepository.findByUsernameOrEmail(username, username).orElse(null);
    }
}

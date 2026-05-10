package com.sigcon.backend.parametrization.notifications.interfaces.controller;

import com.sigcon.backend.parametrization.notifications.domain.repository.NotificationEventCatalogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-PA-18: catalogo de eventos disponibles, agrupado por modulo, para construir el form.
 */
@RestController
@RequestMapping("/api/parametrization/notification-events")
@RequiredArgsConstructor
@Tag(name = "Catalogo eventos notificaciones (Sprint 4)", description = "HU-PA-18 fuente para form de rol")
public class NotificationCatalogController {

    private final NotificationEventCatalogRepository catalogRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "HU-PA-18: lista todos los eventos del catalogo (lista plana o agrupada por modulo)")
    public ResponseEntity<?> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false, defaultValue = "false") boolean grouped) {
        var data = (module == null || module.isBlank())
                ? catalogRepository.findAllByOrderByModuleAscNameAsc()
                : catalogRepository.findByModuleOrderByNameAsc(module);
        // QA Bloque PA Bug 51 (HU-PA-18 E1, 2026-05-09): agrupar por modulo cuando
        // ?grouped=true. Por defecto sigue devolviendo lista plana (compat).
        if (grouped) {
            java.util.Map<String, java.util.List<Object>> groups = new java.util.LinkedHashMap<>();
            for (var evt : data) {
                groups.computeIfAbsent(evt.getModule(), k -> new java.util.ArrayList<>()).add(evt);
            }
            return ResponseEntity.ok(Map.of("groups", groups, "total", data.size()));
        }
        return ResponseEntity.ok(Map.of("data", data));
    }
}

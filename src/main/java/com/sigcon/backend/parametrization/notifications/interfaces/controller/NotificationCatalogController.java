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
    @Operation(summary = "HU-PA-18: lista todos los eventos del catalogo")
    public ResponseEntity<?> list(@RequestParam(required = false) String module) {
        var data = (module == null || module.isBlank())
                ? catalogRepository.findAllByOrderByModuleAscNameAsc()
                : catalogRepository.findByModuleOrderByNameAsc(module);
        return ResponseEntity.ok(Map.of("data", data));
    }
}

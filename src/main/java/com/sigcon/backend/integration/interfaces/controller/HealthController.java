package com.sigcon.backend.integration.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-INT-RF-08: Endpoint de salud para AgroFusion.
 *
 * <p>{@code GET /api/contabilidad/health} retorna {@code {status: UP}} cuando
 * SIGCON esta operativo. AgroFusion consulta este endpoint antes de enviar cada
 * lote (RF-INT-12 R05). Es publico (no requiere autenticacion).
 */
@RestController
@RequestMapping("/api/contabilidad")
@RequiredArgsConstructor
@Tag(name = "Integracion AAEF - Salud",
     description = "Endpoint publico de salud para verificacion de disponibilidad por AgroFusion")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "Health check",
               description = "Verifica que SIGCON este operativo. Incluye conectividad a BD. Publico.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sistema operativo"),
        @ApiResponse(responseCode = "503", description = "Sistema degradado (BD no disponible)")
    })
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());

        boolean dbUp;
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbUp = result != null && result == 1;
        } catch (Exception e) {
            dbUp = false;
        }

        body.put("db", dbUp ? "UP" : "DOWN");
        body.put("status", dbUp ? "UP" : "DOWN");

        if (dbUp) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(503).body(body);
    }
}

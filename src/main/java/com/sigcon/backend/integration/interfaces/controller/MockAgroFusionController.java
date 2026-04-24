package com.sigcon.backend.integration.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fase 7: Mock del callback de AgroFusion.
 *
 * <p>Permite probar el flujo end-to-end sin esperar a que AgroFusion implemente
 * su endpoint real. Apunta {@code AGROFUSION_ACK_CALLBACK_URL} a este endpoint
 * y los ACKs enviados por SIGCON quedan disponibles para inspeccion.
 *
 * <p>Soporta varios modos de respuesta para validar el flujo de retry
 * (HU-INT-RF-13):
 * <ul>
 *   <li>{@code ALWAYS_OK}: responde 200 siempre</li>
 *   <li>{@code FAIL_FIRST_N}: falla en los primeros N intentos por exchangeId, luego acepta</li>
 *   <li>{@code ALWAYS_FAIL}: responde 500 siempre (para validar ACK_FAILED tras 3 intentos)</li>
 *   <li>{@code TIMEOUT}: responde 200 pero con sleep > 30s para forzar timeout del cliente</li>
 * </ul>
 *
 * <p>El modo se controla via parametro de query {@code ?mode=...} o via
 * {@link #setDefaultMode}. Por defecto {@code ALWAYS_OK}.
 *
 * <p><b>NO USAR EN PRODUCCION.</b> Solo para desarrollo/QA mientras AgroFusion
 * no expone su callback real.
 *
 * <p><b>Profile guard:</b> esta clase solo se carga si Spring esta corriendo con
 * el perfil {@code dev} (controlado via {@code SPRING_PROFILES_ACTIVE}).
 * En produccion ({@code SPRING_PROFILES_ACTIVE=PRODUCTION}) Spring NO instancia
 * el bean ni expone los endpoints {@code /mock-agrofusion/**}, por lo que el
 * parametro {@code AGROFUSION_ACK_CALLBACK_URL} debe apuntar al callback REAL
 * de AgroFusion (https://api.agrofusion.co/integrations/aaef/ack).
 */
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "sigcon.integration.mocks-enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("/mock-agrofusion")
@Tag(name = "Mock AgroFusion (solo desarrollo)",
     description = "Callback simulado de AgroFusion para validar HU-INT-RF-07/13. NO usar en produccion. "
                 + "Solo activo con sigcon.integration.mocks-enabled=true.")
public class MockAgroFusionController {

    public enum Mode { ALWAYS_OK, FAIL_FIRST_N, ALWAYS_FAIL, TIMEOUT }

    /** Almacena los ACKs recibidos para inspeccion (sin limite, solo dev). */
    private final List<Map<String, Object>> receivedAcks = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Contador de intentos por exchangeId (para modo FAIL_FIRST_N). */
    private final Map<String, AtomicInteger> attemptsByExchangeId = new ConcurrentHashMap<>();

    /** Modo activo para todos los requests (puede sobreescribirse via query param). */
    private volatile Mode defaultMode = Mode.ALWAYS_OK;

    /** Cuantos intentos fallan en modo FAIL_FIRST_N antes de aceptar (default 2). */
    private volatile int failFirstNAttempts = 2;

    @Operation(
        summary = "Recibir ACK de SIGCON (callback simulado)",
        description = "Endpoint llamado por SIGCON tras procesar un lote AAEF. Persiste el ACK "
                    + "en memoria (lista accesible via /mock-agrofusion/received) y responde "
                    + "segun el modo configurado.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ACK aceptado (ALWAYS_OK o FAIL_FIRST_N tras N intentos)"),
        @ApiResponse(responseCode = "500", description = "ACK rechazado (ALWAYS_FAIL o FAIL_FIRST_N en intento <= N)")
    })
    @PostMapping(value = "/aaef/ack", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receiveAck(
            @RequestBody Map<String, Object> ackPayload,
            @Parameter(description = "Modo de respuesta override (ALWAYS_OK | FAIL_FIRST_N | ALWAYS_FAIL | TIMEOUT)")
            @RequestParam(required = false) Mode mode) throws InterruptedException {

        Mode effective = mode != null ? mode : defaultMode;
        String exchangeId = ackPayload.get("OriginalExchangeId") != null
                ? ackPayload.get("OriginalExchangeId").toString()
                : (ackPayload.get("originalExchangeId") != null ? ackPayload.get("originalExchangeId").toString() : "unknown");

        // Persistir el ACK recibido
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("receivedAt", Instant.now().toString());
        entry.put("exchangeId", exchangeId);
        entry.put("mode", effective.name());
        entry.put("payload", ackPayload);
        receivedAcks.add(entry);

        log.info("Mock AgroFusion: recibido ACK exchangeId={} (mode={})", exchangeId, effective);

        switch (effective) {
            case ALWAYS_OK:
                return ResponseEntity.ok(Map.of("received", true, "exchangeId", exchangeId));

            case FAIL_FIRST_N:
                AtomicInteger ctr = attemptsByExchangeId.computeIfAbsent(
                        exchangeId, k -> new AtomicInteger(0));
                int attempt = ctr.incrementAndGet();
                if (attempt <= failFirstNAttempts) {
                    log.info("Mock AgroFusion: simulando fallo {}/{} para {}",
                            attempt, failFirstNAttempts, exchangeId);
                    return ResponseEntity.status(500).body(Map.of(
                            "error", "Simulated failure",
                            "attempt", attempt));
                }
                return ResponseEntity.ok(Map.of(
                        "received", true, "exchangeId", exchangeId,
                        "acceptedAfterAttempts", attempt));

            case ALWAYS_FAIL:
                return ResponseEntity.status(500).body(Map.of(
                        "error", "Simulated permanent failure",
                        "exchangeId", exchangeId));

            case TIMEOUT:
                Thread.sleep(35_000);
                return ResponseEntity.ok(Map.of("received", true));

            default:
                return ResponseEntity.ok(Map.of("received", true));
        }
    }

    @Operation(
        summary = "Listar ACKs recibidos (auditoria smoke test)",
        description = "Retorna todos los ACKs recibidos en orden cronologico desde el ultimo "
                    + "/mock-agrofusion/clear (o desde el arranque del backend).")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Lista de ACKs"))
    @GetMapping(value = "/received", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listReceived() {
        Map<String, Object> body = new HashMap<>();
        body.put("count", receivedAcks.size());
        body.put("acks", receivedAcks);
        return ResponseEntity.ok(body);
    }

    @Operation(
        summary = "Cambiar modo default y/o failFirstNAttempts",
        description = "Configura el comportamiento por defecto del mock para los siguientes ACKs.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Modo actualizado"))
    @PostMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> setDefaultMode(
            @Parameter(description = "Modo por defecto", example = "FAIL_FIRST_N")
            @RequestParam(required = false) Mode mode,
            @Parameter(description = "Cuantos intentos fallan en FAIL_FIRST_N", example = "2")
            @RequestParam(required = false) Integer failFirstN) {
        if (mode != null) defaultMode = mode;
        if (failFirstN != null && failFirstN >= 0) failFirstNAttempts = failFirstN;
        attemptsByExchangeId.clear();
        Map<String, Object> body = new HashMap<>();
        body.put("defaultMode", defaultMode.name());
        body.put("failFirstNAttempts", failFirstNAttempts);
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Limpiar historial de ACKs recibidos y contadores")
    @PostMapping("/clear")
    public ResponseEntity<?> clear() {
        receivedAcks.clear();
        attemptsByExchangeId.clear();
        return ResponseEntity.ok(Map.of("cleared", true));
    }
}

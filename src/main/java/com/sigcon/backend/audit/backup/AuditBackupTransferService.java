package com.sigcon.backend.audit.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-AU-09 — Transferencia de logs de auditoria a una BD EXTERNA de respaldo,
 * con verificacion de integridad SHA-256. Cubre los 6 escenarios:
 * <ul>
 *   <li>E1 — envio correcto del lote a la BD externa (tabla {@code audit_backup}).</li>
 *   <li>E2 — verificacion del hash SHA-256 en destino (re-lee lo guardado y re-hashea).</li>
 *   <li>E3 — repositorio no disponible (red) → reintento automatico con backoff exponencial.</li>
 *   <li>E4 — autenticacion fallida → reintento y, al agotarse, mensaje "acceso denegado".</li>
 *   <li>E5 — hash origen != destino → marca CORROMPIDO, no avanza cursor, reprograma.</li>
 *   <li>E6 — el hash SHA-256 se calcula EN ORIGEN antes de enviar.</li>
 * </ul>
 *
 * <h3>Decisiones de diseno frente al ejemplo de referencia</h3>
 * <ul>
 *   <li><b>Sin segundo EntityManagerFactory JPA.</b> El ejemplo proponia un segundo
 *       datasource JPA; en esta app (un solo persistence-unit, 145+ entidades) definir
 *       un segundo {@code LocalContainerEntityManagerFactoryBean} desactivaria el EMF
 *       primario por backoff de auto-config. Se usa conexion JDBC directa
 *       ({@link DriverManager}) a la externa solo para la tabla {@code audit_backup}.
 *       Asi el persistence-unit primario queda 100% intacto.</li>
 *   <li><b>Cursor en vez de flag {@code enviado}.</b> {@code audit_logs} es append-only
 *       (HU-AU-01); no se muta. Se avanza {@code audit_backup_cursor.last_transferred_id}
 *       solo cuando un lote queda INTEGRO.</li>
 *   <li><b>Lectura del primario via JdbcTemplate</b> (SQL crudo): evita el {@code @Filter}
 *       multi-tenant y el {@code @PostLoad} de la entidad, permitiendo respaldar los logs
 *       de TODAS las empresas en un solo barrido (el respaldo es cross-tenant por diseno).</li>
 *   <li><b>Retry manual</b> (sin dependencia spring-retry): bucle con backoff exponencial.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditBackupTransferService {

    /** JdbcTemplate del datasource PRIMARIO (lee audit_logs + cursor). */
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Value("${audit-backup.url:jdbc:postgresql://158.69.200.27:5441/backend}")
    private String externalUrl;
    @Value("${audit-backup.username:postgres}")
    private String externalUser;
    @Value("${audit-backup.password:postgres}")
    private String externalPassword;
    @Value("${audit-backup.batch-size:500}")
    private int batchSize;
    @Value("${audit-backup.max-attempts:5}")
    private int maxAttempts;
    @Value("${audit-backup.retry-delay-ms:5000}")
    private long retryDelayMs;

    /**
     * Transfiere a la BD externa los logs de auditoria pendientes (id &gt; cursor).
     * Idempotente y seguro de re-ejecutar: si no hay pendientes, no hace nada.
     *
     * @param trigger origen del disparo ("SCHEDULER" | "MANUAL"), solo para trazas.
     * @return resumen de la operacion (status, cantidad, rango de ids, hash, intento).
     */
    public synchronized Map<String, Object> transferir(String trigger) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trigger", trigger);

        long cursor = readCursor();
        result.put("cursorPrevio", cursor);

        // ── Paso 1: logs pendientes de la BD principal (cross-tenant, SQL crudo) ──
        List<Map<String, Object>> pendientes = jdbc.queryForList(
                "SELECT id, company_id, \"timestamp\", user_id, user_email, action, module, "
                + "severity, entity_type, entity_id, description, hash, previous_hash "
                + "FROM audit_logs WHERE id > ? ORDER BY id ASC LIMIT ?",
                cursor, batchSize);

        if (pendientes.isEmpty()) {
            log.info("[AuditBackup] Sin logs pendientes (cursor={}). No se transfiere.", cursor);
            updateCursorMeta(cursor, "SIN_PENDIENTES", "Sin logs pendientes");
            result.put("status", "SIN_PENDIENTES");
            result.put("transferidos", 0);
            return result;
        }

        long minId = ((Number) pendientes.get(0).get("id")).longValue();
        long maxId = ((Number) pendientes.get(pendientes.size() - 1).get("id")).longValue();
        log.info("[AuditBackup] Logs a transferir: {} (ids {}..{})", pendientes.size(), minId, maxId);

        // ── Paso 2: JSON del lote ──
        final String json;
        try {
            json = objectMapper.writeValueAsString(pendientes);
        } catch (Exception e) {
            log.error("[AuditBackup] No se pudo serializar el lote a JSON", e);
            updateCursorMeta(cursor, "ERROR", "Serializacion JSON fallida: " + e.getMessage());
            result.put("status", "ERROR");
            result.put("message", "Serializacion JSON fallida");
            return result;
        }

        // ── Paso 3 (E6): hash SHA-256 EN ORIGEN, antes de enviar ──
        final String hashOrigen = sha256(json);
        result.put("hashOrigen", hashOrigen);
        result.put("idOrigenMin", minId);
        result.put("idOrigenMax", maxId);
        result.put("cantidad", pendientes.size());

        // ── Reintentos (E3/E4): backoff exponencial ──
        RuntimeException ultimoError = null;
        for (int intento = 1; intento <= maxAttempts; intento++) {
            try {
                String hashDestino = enviarYVerificar(json, hashOrigen, pendientes.size(), minId, maxId);
                // ── E2 OK: INTEGRO. Avanzar cursor solo aqui ──
                advanceCursor(maxId, "INTEGRO",
                        "Lote " + minId + ".." + maxId + " (" + pendientes.size() + " regs) INTEGRO");
                log.info("[AuditBackup] Transferencia OK. {} registros | hash={} | intento={}",
                        pendientes.size(), hashDestino, intento);
                result.put("status", "INTEGRO");
                result.put("transferidos", pendientes.size());
                result.put("hashDestino", hashDestino);
                result.put("intentos", intento);
                result.put("cursorNuevo", maxId);
                return result;
            } catch (IntegridadException ie) {
                // E5: hashes no coinciden. Reintentar (puede ser corrupcion transitoria).
                ultimoError = ie;
                log.error("[AuditBackup] {} (intento {}/{})", ie.getMessage(), intento, maxAttempts);
            } catch (RuntimeException re) {
                // E3/E4: red caida o auth fallida.
                ultimoError = re;
                log.warn("[AuditBackup] No se pudo transmitir el log, reintento en curso (intento {}/{}): {}",
                        intento, maxAttempts, re.getMessage());
            }
            if (intento < maxAttempts) {
                sleep(retryDelayMs * (long) Math.pow(2, intento - 1));
            }
        }

        // ── Recover: agotados los reintentos. Distinguir auth (E4) vs red (E3). ──
        String causa = ultimoError == null ? "desconocida" : String.valueOf(ultimoError.getMessage());
        boolean authFallida = causa != null && (causa.toLowerCase().contains("denegado")
                || causa.toLowerCase().contains("password")
                || causa.toLowerCase().contains("authentication")
                || causa.toLowerCase().contains("autenticaci"));
        String finalMsg = authFallida
                ? "Acceso denegado al repositorio externo. Actualice las credenciales. Causa: " + causa
                : "No se pudo transmitir el log tras " + maxAttempts + " intentos. Causa: " + causa;
        log.error("[AuditBackup] {}", finalMsg);
        updateCursorMeta(cursor, "FALLIDO", finalMsg);   // NO avanza cursor
        result.put("status", "FALLIDO");
        result.put("message", finalMsg);
        result.put("transferidos", 0);
        return result;
    }

    /**
     * Envia el lote a la BD externa (INSERT estado=ENVIADO), re-lee lo guardado,
     * recalcula el hash SHA-256 (E2) y compara (E5). Devuelve el hash destino.
     * Lanza {@link IntegridadException} si los hashes no coinciden.
     */
    private String enviarYVerificar(String json, String hashOrigen, int cantidad, long minId, long maxId) {
        try (Connection c = DriverManager.getConnection(externalUrl, externalUser, externalPassword)) {
            c.setAutoCommit(false);

            // ── Paso 4 (E1): INSERT en la externa ──
            long backupId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO audit_backup (contenido_json, hash_sha256_origen, fecha_envio, "
                    + "estado, cantidad_registros, id_origen_min, id_origen_max) "
                    + "VALUES (?,?,?,?,?,?,?) RETURNING id")) {
                ps.setString(1, json);
                ps.setString(2, hashOrigen);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(4, "ENVIADO");
                ps.setInt(5, cantidad);
                ps.setLong(6, minId);
                ps.setLong(7, maxId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    backupId = rs.getLong(1);
                }
            }

            // ── Paso 5 (E2): re-leer lo realmente guardado y recalcular hash ──
            String almacenado;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT contenido_json FROM audit_backup WHERE id = ?")) {
                ps.setLong(1, backupId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    almacenado = rs.getString(1);
                }
            }
            String hashDestino = sha256(almacenado);

            if (!hashOrigen.equals(hashDestino)) {
                // ── E5: integridad rota. Marcar CORROMPIDO y abortar (reintento). ──
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE audit_backup SET estado='CORROMPIDO', hash_sha256_destino=? WHERE id=?")) {
                    ps.setString(1, hashDestino);
                    ps.setLong(2, backupId);
                    ps.executeUpdate();
                }
                c.commit();
                throw new IntegridadException(String.format(
                        "Error de integridad: copia invalida, lote descartado. "
                        + "backupId=%d | hashOrigen=%s | hashDestino=%s | hora=%s",
                        backupId, hashOrigen, hashDestino, LocalDateTime.now()));
            }

            // ── Paso 6 (E2 OK): marcar INTEGRO ──
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE audit_backup SET estado='INTEGRO', hash_sha256_destino=? WHERE id=?")) {
                ps.setString(1, hashDestino);
                ps.setLong(2, backupId);
                ps.executeUpdate();
            }
            c.commit();
            return hashDestino;
        } catch (IntegridadException ie) {
            throw ie;
        } catch (Exception e) {
            // SQLException de red/auth → RuntimeException para que el retry la capture.
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    // ── Cursor (BD primaria) ──────────────────────────────────────────────────

    private long readCursor() {
        Long v = jdbc.queryForObject(
                "SELECT last_transferred_id FROM audit_backup_cursor WHERE id = 1", Long.class);
        return v == null ? 0L : v;
    }

    private void advanceCursor(long newId, String status, String message) {
        jdbc.update("UPDATE audit_backup_cursor SET last_transferred_id=?, last_run_at=NOW(), "
                + "last_status=?, last_message=?, updated_at=NOW() WHERE id=1",
                newId, status, trunc(message, 500));
    }

    private void updateCursorMeta(long keepId, String status, String message) {
        jdbc.update("UPDATE audit_backup_cursor SET last_transferred_id=?, last_run_at=NOW(), "
                + "last_status=?, last_message=?, updated_at=NOW() WHERE id=1",
                keepId, status, trunc(message, 500));
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    /** HU-AU-09 E6: SHA-256 hex de un String UTF-8. */
    private String sha256(String contenido) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Error calculando SHA-256", e);
        }
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Marca de hash origen != destino (HU-AU-09 E5). */
    private static class IntegridadException extends RuntimeException {
        IntegridadException(String m) { super(m); }
    }
}

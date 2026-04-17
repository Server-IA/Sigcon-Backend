package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.domain.model.AuditLog;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * HU-AU-01 E5/E6: Servicio de hash SHA-256 encadenado para garantizar
 * la inmutabilidad e integridad de la cadena de auditoria.
 *
 * <p>Cada registro se encadena con el anterior: {@code hash = SHA256(previousHash
 * + timestamp + action + entityType + entityId + userId)}. El primer registro
 * usa {@code "GENESIS"} como {@code previousHash}.
 *
 * <p>Si alguien modifica un registro intermedio, todos los hashes posteriores
 * se invalidan, haciendo evidente la manipulacion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditHashService {

    public static final String GENESIS = "GENESIS";
    private final AuditLogRepository repository;

    /**
     * Obtiene el hash del ultimo registro de la cadena.
     * Si no hay registros, retorna "GENESIS" (inicio de la cadena).
     */
    public String getLastHash() {
        return repository.findTopByOrderByIdDesc()
                .map(AuditLog::getHash)
                .orElse(GENESIS);
    }

    /**
     * Calcula el hash SHA-256 para un nuevo registro, encadenandolo
     * con el hash anterior.
     *
     * @param previousHash hash del registro N-1 (o "GENESIS")
     * @param timestamp    momento del evento
     * @param action       tipo de accion
     * @param entityType   entidad afectada
     * @param entityId     id de la entidad
     * @param userId       id del usuario que ejecuto
     * @return hash SHA-256 en hexadecimal (64 caracteres)
     */
    public String computeHash(String previousHash, LocalDateTime timestamp,
                               AuditAction action, String entityType,
                               Long entityId, Long userId) {
        String payload = String.join("|",
                previousHash != null ? previousHash : GENESIS,
                timestamp != null ? timestamp.toString() : "",
                action != null ? action.name() : "",
                entityType != null ? entityType : "",
                entityId != null ? entityId.toString() : "",
                userId != null ? userId.toString() : "");
        return sha256(payload);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
}

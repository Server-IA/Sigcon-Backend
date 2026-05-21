package com.sigcon.backend.banks.archivos_soporte.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.archivos_soporte.application.ArchivoSoporteDTO;
import com.sigcon.backend.banks.archivos_soporte.domain.model.ArchivoSoporte;
import com.sigcon.backend.banks.archivos_soporte.domain.repository.ArchivoSoporteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BNK-HU-062 / BNK-HU-063: conservación de soportes (extracto, CSV, informes)
 * con hash SHA-256 inalterable, retención 10 años, verificación de integridad
 * bajo demanda y bloqueo de borrado antes de la retención.
 *
 * <p>La replicación a medio alterno (E2/E3) y el WORM con Object Lock requieren
 * infraestructura cloud/almacenamiento no disponible en el stack local; aquí se
 * conserva el archivo inmutable a nivel de aplicación (sin endpoint de update,
 * borrado bloqueado antes de la retención).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchivoSoporteService {

    private final ArchivoSoporteRepository repository;
    private final AuditPublisher auditPublisher;

    /**
     * BNK-HU-062 E1/E2: guarda el archivo con su hash SHA-256 y retención 10 años.
     * Usado internamente al importar un extracto/CSV. No falla el flujo de negocio
     * si el guardado del soporte falla (defensivo).
     */
    @Transactional
    public ArchivoSoporte store(byte[] bytes, String fileName, String mime, String tipo,
                                Long bankAccountId, Long sessionId, Long uploadedBy) {
        String hash = sha256Hex(bytes);
        ArchivoSoporte a = ArchivoSoporte.builder()
                .tipo(tipo)
                .fileName(fileName)
                .mimeType(mime)
                .fileContent(bytes)
                .hashSha256(hash)
                .fileSize((long) bytes.length)
                .bankAccountId(bankAccountId)
                .reconciliationSessionId(sessionId)
                .uploadedBy(uploadedBy)
                .build();
        a = repository.save(a);
        auditPublisher.publishCreate(AuditModule.BNK, "ArchivoSoporte", a.getId(),
                "Soporte conservado " + tipo + " '" + fileName + "' hash=" + hash
                        + " retenerHasta=" + a.getRetenerHasta());
        return a;
    }

    /** Lista soportes de una cuenta (metadatos). */
    public List<ArchivoSoporteDTO> listByBankAccount(Long bankAccountId) {
        return repository.findByBankAccountIdAndDeletedAtIsNullOrderByUploadedAtDesc(bankAccountId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * BNK-HU-062 E4: verifica integridad recalculando el hash del archivo
     * almacenado y comparándolo con el hash registrado.
     */
    public Map<String, Object> verifyIntegrity(Long id) {
        ArchivoSoporte a = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Soporte no encontrado"));
        String recomputed = sha256Hex(a.getFileContent());
        boolean integro = recomputed.equals(a.getHashSha256());
        Map<String, Object> r = new HashMap<>();
        r.put("integro", integro);
        r.put("hashRegistrado", a.getHashSha256());
        r.put("hashRecalculado", recomputed);
        r.put("uploadedAt", a.getUploadedAt());
        r.put("message", integro
                ? "Archivo íntegro desde " + a.getUploadedAt()
                : "ALERTA: el archivo fue manipulado. El hash no coincide con el registrado al cargar.");
        if (!integro) {
            // Alerta crítica de manipulación (E4).
            auditPublisher.publish(AuditAction.UPDATE, AuditModule.BNK,
                    com.sigcon.backend.audit.domain.model.enums.AuditSeverity.CRITICAL,
                    "ArchivoSoporte", id,
                    "INTEGRIDAD_ARCHIVO_COMPROMETIDA: el soporte id=" + id + " (" + a.getFileName()
                            + ") no coincide con su hash. Posible manipulación.",
                    null, null, null);
        }
        return r;
    }

    /**
     * BNK-HU-062 E6: obtiene el archivo para descarga y registra el acceso en
     * auditoría (EXPORTAR). El controller arma la respuesta binaria.
     */
    @Transactional
    public ArchivoSoporte getForDownloadAndAudit(Long id) {
        ArchivoSoporte a = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Soporte no encontrado"));
        auditPublisher.publish(AuditAction.EXPORT, AuditModule.BNK, null,
                "ArchivoSoporte", id,
                "Descarga de soporte '" + a.getFileName() + "' (" + a.getTipo() + ")",
                null, null, null);
        return a;
    }

    /**
     * BNK-HU-063 E5: bloquea el borrado físico antes de que venza la retención.
     * Requiere acta firmada por revisor fiscal + autorización admin (no se ejecuta
     * borrado físico automático; se registra la solicitud).
     */
    @Transactional
    public void blockedDelete(Long id, String acta) {
        ArchivoSoporte a = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Soporte no encontrado"));
        if (a.getRetenerHasta() != null && a.getRetenerHasta().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("No se puede eliminar el soporte: la retención vence el "
                    + a.getRetenerHasta().toLocalDate()
                    + ". La eliminación antes de esa fecha está prohibida (conservación 10 años).");
        }
        if (acta == null || acta.trim().length() < 20) {
            throw new IllegalArgumentException("Para eliminar un soporte con retención vencida se requiere "
                    + "el acta firmada por revisor fiscal (mínimo 20 caracteres).");
        }
        a.setDeletedAt(LocalDateTime.now());
        repository.save(a);
        auditPublisher.publishDelete(AuditModule.BNK, "ArchivoSoporte", id,
                "Soporte eliminado tras retención vencida. Acta: " + acta);
    }

    /** BNK-HU-063 E6: reporte de retención y backup. */
    public Map<String, Object> retentionReport() {
        Map<String, Object> r = new HashMap<>();
        r.put("totalArchivos", repository.countByDeletedAtIsNull());
        r.put("totalBytesAlmacenados", repository.totalBytesAlmacenados());
        r.put("proximosAVencer6Meses", repository.countProximosAVencer(LocalDateTime.now().plusMonths(6)));
        r.put("nota", "Replicación a medio alterno (backup multi-nube) requiere infraestructura no disponible en el entorno local.");
        return r;
    }

    private ArchivoSoporteDTO toDto(ArchivoSoporte a) {
        return ArchivoSoporteDTO.builder()
                .id(a.getId())
                .tipo(a.getTipo())
                .fileName(a.getFileName())
                .mimeType(a.getMimeType())
                .hashSha256(a.getHashSha256())
                .fileSize(a.getFileSize())
                .bankAccountId(a.getBankAccountId())
                .reconciliationSessionId(a.getReconciliationSessionId())
                .uploadedAt(a.getUploadedAt())
                .retenerHasta(a.getRetenerHasta())
                .replicationStatus(a.getReplicationStatus())
                .build();
    }

    /** SHA-256 en hexadecimal (64 chars). */
    public static String sha256Hex(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data != null ? data : new byte[0]);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

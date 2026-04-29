package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.application.AuditFindingDTO;
import com.sigcon.backend.audit.application.CreateAuditFindingRequest;
import com.sigcon.backend.audit.domain.model.AuditFinding;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.repository.AuditFindingRepository;
import com.sigcon.backend.audit.domain.repository.AuditLogRepository;
import com.sigcon.backend.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HU-AU-08 E4 (2026-04-28): Servicio de gestion de hallazgos de auditoria.
 *
 * <p>Flujo de estados:
 * <pre>
 *   ABIERTO -> EN_REVISION -> CERRADO
 * </pre>
 *
 * <p>Cada transicion de estado registra un evento de auditoria via
 * {@link AuditLogService} para mantener trazabilidad completa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditFindingService {

    private final AuditFindingRepository repository;
    private final AuditLogRepository logRepository;
    private final AuditLogService auditLogService;
    private final UserUtil userUtil;

    /** HU-AU-08 E4: crear nuevo hallazgo en estado ABIERTO. */
    @Transactional
    public AuditFindingDTO create(CreateAuditFindingRequest req) {
        // Validar que el log exista
        if (!logRepository.existsById(req.getAuditLogId())) {
            throw new IllegalArgumentException(
                    "El log de auditoria id=" + req.getAuditLogId() + " no existe");
        }
        String currentUser = currentUserEmail();
        AuditFinding f = AuditFinding.builder()
                .auditLogId(req.getAuditLogId())
                .title(req.getTitle())
                .description(req.getDescription())
                .severity(req.getSeverity() != null ? req.getSeverity() : "MEDIUM")
                .status("ABIERTO")
                .openedBy(currentUser)
                .assignedTo(req.getAssignedTo())
                .openedAt(LocalDateTime.now())
                .build();
        AuditFinding saved = repository.save(f);
        try {
            auditLogService.register(AuditAction.CREATE, AuditModule.AU, null,
                    "AuditFinding", saved.getId(),
                    "Hallazgo abierto: " + saved.getTitle()
                            + " (severidad=" + saved.getSeverity()
                            + ", logId=" + req.getAuditLogId() + ")",
                    null, null, null);
        } catch (Exception e) {
            log.warn("HU-AU-08 E4: no se pudo registrar audit log de creacion: {}", e.getMessage());
        }
        return toDTO(saved);
    }

    /** Listado paginado. */
    public Page<AuditFindingDTO> list(int page, int size, String status) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditFinding> result;
        if (status != null && !status.isBlank()) {
            result = repository.findAll((root, q, cb) ->
                    cb.equal(root.get("status"), status), pageable);
        } else {
            result = repository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable);
        }
        return result.map(this::toDTO);
    }

    public AuditFindingDTO findById(Long id) {
        return repository.findById(id).map(this::toDTO)
                .orElseThrow(() -> new IllegalArgumentException("Hallazgo no encontrado"));
    }

    public List<AuditFindingDTO> findByAuditLogId(Long auditLogId) {
        return repository.findByAuditLogIdAndDeletedAtIsNullOrderByCreatedAtDesc(auditLogId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** ABIERTO -> EN_REVISION. */
    @Transactional
    public AuditFindingDTO startReview(Long id, String reviewer) {
        AuditFinding f = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hallazgo no encontrado"));
        if (!"ABIERTO".equals(f.getStatus())) {
            throw new IllegalStateException(
                    "Solo se pueden poner en revision hallazgos en estado ABIERTO. Estado actual: "
                    + f.getStatus());
        }
        f.setStatus("EN_REVISION");
        f.setReviewStartedAt(LocalDateTime.now());
        if (reviewer != null && !reviewer.isBlank()) f.setAssignedTo(reviewer);
        AuditFinding saved = repository.save(f);
        try {
            auditLogService.register(AuditAction.UPDATE, AuditModule.AU, null,
                    "AuditFinding", saved.getId(),
                    "Hallazgo en revision: " + saved.getTitle()
                            + " (revisor=" + saved.getAssignedTo() + ")",
                    null, null, null);
        } catch (Exception ignored) {}
        return toDTO(saved);
    }

    /** EN_REVISION -> CERRADO con resolucion. */
    @Transactional
    public AuditFindingDTO close(Long id, String resolution) {
        if (resolution == null || resolution.isBlank() || resolution.trim().length() < 10) {
            throw new IllegalArgumentException(
                    "Debe especificar la resolucion (minimo 10 caracteres) al cerrar el hallazgo");
        }
        AuditFinding f = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hallazgo no encontrado"));
        if ("CERRADO".equals(f.getStatus())) {
            throw new IllegalStateException("El hallazgo ya esta cerrado");
        }
        f.setStatus("CERRADO");
        f.setResolution(resolution);
        f.setClosedAt(LocalDateTime.now());
        f.setClosedBy(currentUserEmail());
        AuditFinding saved = repository.save(f);
        try {
            auditLogService.register(AuditAction.UPDATE, AuditModule.AU, null,
                    "AuditFinding", saved.getId(),
                    "Hallazgo CERRADO: " + saved.getTitle()
                            + " | resolucion=" + truncate(resolution, 200),
                    null, null, null);
        } catch (Exception ignored) {}
        return toDTO(saved);
    }

    /** Eliminar (soft) — solo permitido en estado ABIERTO. */
    @Transactional
    public void delete(Long id) {
        AuditFinding f = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hallazgo no encontrado"));
        if (!"ABIERTO".equals(f.getStatus())) {
            throw new IllegalStateException(
                    "Solo se pueden eliminar hallazgos en estado ABIERTO. Use cerrar para los demas estados.");
        }
        repository.delete(f);
        try {
            auditLogService.register(AuditAction.DELETE, AuditModule.AU, null,
                    "AuditFinding", id,
                    "Hallazgo eliminado: " + f.getTitle(),
                    null, null, null);
        } catch (Exception ignored) {}
    }

    private AuditFindingDTO toDTO(AuditFinding f) {
        return AuditFindingDTO.builder()
                .id(f.getId())
                .auditLogId(f.getAuditLogId())
                .title(f.getTitle())
                .description(f.getDescription())
                .status(f.getStatus())
                .severity(f.getSeverity())
                .openedBy(f.getOpenedBy())
                .assignedTo(f.getAssignedTo())
                .closedBy(f.getClosedBy())
                .resolution(f.getResolution())
                .openedAt(f.getOpenedAt())
                .reviewStartedAt(f.getReviewStartedAt())
                .closedAt(f.getClosedAt())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    private String currentUserEmail() {
        try {
            return userUtil.getUser().getEmail();
        } catch (Exception e) {
            return "system";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}

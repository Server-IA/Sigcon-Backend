package com.sigcon.backend.general.accounting.journal.attachments.domain.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.general.accounting.journal.attachments.application.JournalEntrySupportDTO;
import com.sigcon.backend.general.accounting.journal.attachments.domain.model.JournalEntrySupport;
import com.sigcon.backend.general.accounting.journal.attachments.domain.repository.JournalEntrySupportRepository;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HU-CG-05A/B/C: servicio para gestion de comprobantes adjuntos a asientos
 * contables. Valida tipo MIME (PDF/JPG/PNG) y tamaño maximo 5MB por archivo.
 *
 * <p>Replica del patron AR-03 (SalesInvoiceAttachmentService) adaptado al
 * dominio de comprobantes contables (JournalEntry).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalEntrySupportService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    /** Tamaño maximo permitido: 5MB por archivo. */
    private static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;

    private final JournalEntrySupportRepository attachmentRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AuditPublisher auditPublisher;

    /**
     * HU-CG-05A: adjunta un archivo a un asiento contable. Valida MIME, tamaño
     * y existencia del comprobante. Persiste con el companyId del tenant actual.
     */
    @Transactional
    public ResponseEntity<?> upload(Long journalEntryId, MultipartFile file,
                                     String supportType, String description) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un archivo");
        }
        if (!journalEntryRepository.existsById(journalEntryId)) {
            throw new IllegalArgumentException("El comprobante contable no fue encontrado");
        }
        String mime = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_MIME.contains(mime)) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido. Solo se aceptan PDF, JPG o PNG.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "El archivo supera el tamaño maximo permitido (5MB).");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Error leyendo el archivo: " + e.getMessage());
        }

        JournalEntrySupport attachment = JournalEntrySupport.builder()
                .journalEntryId(journalEntryId)
                .fileName(file.getOriginalFilename())
                .mimeType(mime)
                .fileSize(file.getSize())
                .fileContent(content)
                .supportType(supportType != null && !supportType.isBlank() ? supportType.trim() : "OTRO")
                .description(description)
                .uploadedBy(currentUsername())
                .build();

        attachment = attachmentRepository.save(attachment);

        auditPublisher.publishCreate(AuditModule.CG, "JournalEntrySupport", attachment.getId(),
                "Soporte agregado al comprobante contable #" + journalEntryId
                        + " archivo=" + attachment.getFileName()
                        + (supportType != null ? " tipo=" + supportType : ""));
        log.info("Adjunto JE {} cargado por {}", attachment.getId(), attachment.getUploadedBy());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Soporte adjuntado correctamente"),
                Optional.of(toDto(attachment))));
    }

    /**
     * HU-CG-05C: lista los soportes vigentes de un comprobante contable.
     */
    public ResponseEntity<?> listByJournalEntry(Long journalEntryId) {
        List<JournalEntrySupportDTO> list = attachmentRepository
                .findByJournalEntryIdAndDeletedAtIsNullOrderByUploadedAtDesc(journalEntryId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Soportes obtenidos"),
                Optional.of(list)));
    }

    /** HU-CG-05C: descarga el contenido binario del adjunto. */
    public JournalEntrySupport getForDownload(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("El soporte no fue encontrado"));
    }

    /** HU-CG-05A: elimina logicamente un soporte. */
    @Transactional
    public ResponseEntity<?> delete(Long attachmentId) {
        if (!attachmentRepository.existsById(attachmentId)) {
            throw new IllegalArgumentException("El soporte no fue encontrado");
        }
        attachmentRepository.deleteById(attachmentId);
        auditPublisher.publishDelete(AuditModule.CG, "JournalEntrySupport", attachmentId,
                "Soporte de comprobante contable eliminado id=" + attachmentId);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Soporte eliminado correctamente"), Optional.empty()));
    }

    /**
     * HU-CG-02A E2 / HU-CG-05B: cuenta soportes vigentes. Usado por
     * JournalEntryService.postEntry para validar que haya al menos un soporte
     * antes de contabilizar el asiento.
     */
    public long countByJournalEntry(Long journalEntryId) {
        return attachmentRepository.countByJournalEntryIdAndDeletedAtIsNull(journalEntryId);
    }

    private JournalEntrySupportDTO toDto(JournalEntrySupport a) {
        return JournalEntrySupportDTO.builder()
                .id(a.getId())
                .journalEntryId(a.getJournalEntryId())
                .fileName(a.getFileName())
                .mimeType(a.getMimeType())
                .fileSize(a.getFileSize())
                .supportType(a.getSupportType())
                .description(a.getDescription())
                .uploadedBy(a.getUploadedBy())
                .uploadedAt(a.getUploadedAt())
                .build();
    }

    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) return auth.getName();
        } catch (Exception ignored) { }
        return "sistema";
    }
}

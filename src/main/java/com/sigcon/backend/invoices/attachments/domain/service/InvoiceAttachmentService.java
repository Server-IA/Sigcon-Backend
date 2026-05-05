package com.sigcon.backend.invoices.attachments.domain.service;

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
import com.sigcon.backend.invoices.attachments.application.InvoiceAttachmentDTO;
import com.sigcon.backend.invoices.attachments.domain.model.InvoiceAttachment;
import com.sigcon.backend.invoices.attachments.domain.repository.InvoiceAttachmentRepository;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AP-13: Servicio para gestion de documentos soporte adjuntos a facturas de compra.
 *
 * <p>Soporta los tipos de documento: {@code PURCHASE_ORDER}, {@code RECEPTION_ACT},
 * {@code CONTRACT}, {@code OTHER}.
 *
 * <p>Reglas de validacion:
 * <ul>
 *   <li>MIME permitido: PDF, JPG, PNG</li>
 *   <li>Tamaño maximo: 5 MB por archivo</li>
 *   <li>La factura debe existir antes de permitir carga</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceAttachmentService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "PURCHASE_ORDER",
            "RECEPTION_ACT",
            "CONTRACT",
            "OTHER"
    );

    /** Tamaño maximo permitido: 5MB. */
    private static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;

    private final InvoiceAttachmentRepository attachmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditPublisher auditPublisher;

    /**
     * AP-13: Adjunta un documento soporte a una factura de compra.
     *
     * @param invoiceId    ID de la factura de compra
     * @param file         archivo multipart (PDF/JPG/PNG, max 5MB)
     * @param documentType tipo de documento (PURCHASE_ORDER/RECEPTION_ACT/CONTRACT/OTHER)
     * @param description  descripcion opcional del documento
     * @return DTO del adjunto persistido
     */
    @Transactional
    public ResponseEntity<?> upload(Long invoiceId, MultipartFile file,
                                     String documentType, String description) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un archivo");
        }
        if (!invoiceRepository.existsById(invoiceId)) {
            throw new IllegalArgumentException("La factura de compra no fue encontrada");
        }

        String docType = documentType == null ? "OTHER" : documentType.toUpperCase();
        if (!ALLOWED_DOCUMENT_TYPES.contains(docType)) {
            throw new IllegalArgumentException(
                    "Tipo de documento no permitido. Valores: " + ALLOWED_DOCUMENT_TYPES);
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

        // HU-AP-12 E3 (Bloque AR): calcular hash SHA-256 del contenido y
        // bloquear si ya existe el mismo archivo (mismo contenido) en la
        // empresa, mensaje literal HU.
        String fileHash = computeSha256(content);
        var dups = attachmentRepository.findByFileHashAndReplacedByIdIsNullAndDeletedAtIsNull(fileHash);
        if (!dups.isEmpty()) {
            InvoiceAttachment existing = dups.get(0);
            throw new IllegalArgumentException(
                    "Este documento ya fue adjuntado a otra factura "
                    + "(factura #" + existing.getInvoiceId() + ", archivo " + existing.getFileName() + ")");
        }

        InvoiceAttachment attachment = InvoiceAttachment.builder()
                .invoiceId(invoiceId)
                .documentType(docType)
                .fileName(file.getOriginalFilename())
                .mimeType(mime)
                .fileSize(file.getSize())
                .fileContent(content)
                .description(description)
                .uploadedBy(currentUsername())
                .fileHash(fileHash)
                .version(1)
                .build();

        attachment = attachmentRepository.save(attachment);

        auditPublisher.publishCreate(AuditModule.AP, "InvoiceAttachment", attachment.getId(),
                "Adjunto agregado a factura AP #" + invoiceId + " tipo=" + docType
                        + " archivo=" + attachment.getFileName());
        log.info("Documento soporte {} (tipo {}) adjuntado a factura AP {}",
                attachment.getId(), docType, invoiceId);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Documento soporte adjuntado correctamente"),
                Optional.of(toDto(attachment))));
    }

    /**
     * HU-AP-12 E4 (Bloque AR): reemplaza un adjunto existente por una nueva
     * version. El adjunto previo se marca como replaced_by_id apuntando al
     * nuevo y queda como historico. El nuevo arranca en version anterior + 1.
     *
     * <p>Caso de uso tipico: el proveedor envia una version corregida del acta
     * de recepcion y el contador necesita reemplazar el documento sin perder
     * trazabilidad de la version original.
     */
    @Transactional
    public ResponseEntity<?> replace(Long previousAttachmentId, MultipartFile file,
                                      String description) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un archivo");
        }
        InvoiceAttachment previous = attachmentRepository.findById(previousAttachmentId)
                .orElseThrow(() -> new IllegalArgumentException("El adjunto previo no fue encontrado"));
        if (previous.getReplacedById() != null) {
            throw new IllegalStateException(
                    "Este adjunto ya fue reemplazado por la version #" + previous.getReplacedById());
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
        String fileHash = computeSha256(content);

        InvoiceAttachment newer = InvoiceAttachment.builder()
                .invoiceId(previous.getInvoiceId())
                .documentType(previous.getDocumentType())
                .fileName(file.getOriginalFilename())
                .mimeType(mime)
                .fileSize(file.getSize())
                .fileContent(content)
                .description(description != null ? description : previous.getDescription())
                .uploadedBy(currentUsername())
                .fileHash(fileHash)
                .version((previous.getVersion() != null ? previous.getVersion() : 1) + 1)
                .build();
        newer = attachmentRepository.save(newer);

        previous.setReplacedById(newer.getId());
        attachmentRepository.save(previous);

        auditPublisher.publishUpdate(AuditModule.AP, "InvoiceAttachment", newer.getId(),
                "Adjunto reemplazado: " + previous.getFileName() + " (v" + previous.getVersion()
                + ") -> " + newer.getFileName() + " (v" + newer.getVersion() + ")");
        log.info("HU-AP-12 E4: adjunto {} reemplazado por {} (v{})", previous.getId(),
                newer.getId(), newer.getVersion());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Documento reemplazado correctamente. Nueva version guardada."),
                Optional.of(toDto(newer))));
    }

    /** HU-AP-12 E3 (Bloque AR): SHA-256 hex en lowercase del contenido. */
    private String computeSha256(byte[] content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /**
     * AP-13: Lista los documentos soporte de una factura. Si se proporciona
     * {@code documentType}, filtra por ese tipo.
     */
    public ResponseEntity<?> listByInvoice(Long invoiceId, String documentType) {
        List<InvoiceAttachment> items = (documentType == null || documentType.isBlank())
                ? attachmentRepository.findByInvoiceIdAndDeletedAtIsNull(invoiceId)
                : attachmentRepository.findByInvoiceIdAndDocumentTypeAndDeletedAtIsNull(
                        invoiceId, documentType.toUpperCase());
        List<InvoiceAttachmentDTO> list = items.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Documentos soporte obtenidos"), Optional.of(list)));
    }

    /** AP-13: Recupera un adjunto por id para descarga (contenido binario). */
    public InvoiceAttachment getForDownload(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("El adjunto no fue encontrado"));
    }

    /** AP-13: Elimina logicamente un adjunto. */
    @Transactional
    public ResponseEntity<?> delete(Long attachmentId) {
        if (!attachmentRepository.existsById(attachmentId)) {
            throw new IllegalArgumentException("El adjunto no fue encontrado");
        }
        attachmentRepository.deleteById(attachmentId);
        auditPublisher.publishDelete(AuditModule.AP, "InvoiceAttachment", attachmentId,
                "Adjunto eliminado id=" + attachmentId);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Adjunto eliminado correctamente"), Optional.empty()));
    }

    private InvoiceAttachmentDTO toDto(InvoiceAttachment a) {
        return InvoiceAttachmentDTO.builder()
                .id(a.getId())
                .invoiceId(a.getInvoiceId())
                .documentType(a.getDocumentType())
                .fileName(a.getFileName())
                .mimeType(a.getMimeType())
                .fileSize(a.getFileSize())
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

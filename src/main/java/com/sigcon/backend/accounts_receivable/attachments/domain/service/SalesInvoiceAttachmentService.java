package com.sigcon.backend.accounts_receivable.attachments.domain.service;

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

import com.sigcon.backend.accounts_receivable.attachments.application.SalesInvoiceAttachmentDTO;
import com.sigcon.backend.accounts_receivable.attachments.domain.model.SalesInvoiceAttachment;
import com.sigcon.backend.accounts_receivable.attachments.domain.repository.SalesInvoiceAttachmentRepository;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AR-03: Servicio para gestion de comprobantes adjuntos a facturas de venta.
 * Valida tipo MIME (PDF/JPG/PNG) y tamaño maximo 5MB por archivo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesInvoiceAttachmentService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    /** Tamaño maximo permitido: 5MB. */
    private static final long MAX_SIZE_BYTES = 5L * 1024L * 1024L;

    private final SalesInvoiceAttachmentRepository attachmentRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;

    /**
     * AR-03: Adjunta un archivo a una factura de venta.
     *
     * @param invoiceId identificador de la factura
     * @param file      archivo multipart cargado
     * @return adjunto persistido
     */
    @Transactional
    public ResponseEntity<?> upload(Long invoiceId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar un archivo");
        }
        if (!salesInvoiceRepository.existsById(invoiceId)) {
            throw new IllegalArgumentException("La factura de venta no fue encontrada");
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

        SalesInvoiceAttachment attachment = SalesInvoiceAttachment.builder()
                .salesInvoiceId(invoiceId)
                .fileName(file.getOriginalFilename())
                .mimeType(mime)
                .fileSize(file.getSize())
                .fileContent(content)
                .uploadedBy(currentUsername())
                .build();

        attachment = attachmentRepository.save(attachment);
        log.info("Adjunto {} cargado para FV {}", attachment.getId(), invoiceId);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Comprobante adjuntado correctamente"),
                Optional.of(toDto(attachment))));
    }

    /**
     * AR-03: Lista los adjuntos vigentes de una factura.
     *
     * @param invoiceId identificador de la factura
     * @return lista de DTOs (sin contenido binario)
     */
    public ResponseEntity<?> listByInvoice(Long invoiceId) {
        List<SalesInvoiceAttachmentDTO> list = attachmentRepository
                .findBySalesInvoiceIdAndDeletedAtIsNull(invoiceId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Adjuntos obtenidos"), Optional.of(list)));
    }

    /**
     * AR-03: Recupera un adjunto por id para descarga.
     *
     * @param attachmentId identificador del adjunto
     * @return entidad con contenido binario
     */
    public SalesInvoiceAttachment getForDownload(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("El adjunto no fue encontrado"));
    }

    /**
     * AR-03: Elimina logicamente un adjunto.
     *
     * @param attachmentId identificador del adjunto
     * @return respuesta estandar
     */
    @Transactional
    public ResponseEntity<?> delete(Long attachmentId) {
        if (!attachmentRepository.existsById(attachmentId)) {
            throw new IllegalArgumentException("El adjunto no fue encontrado");
        }
        attachmentRepository.deleteById(attachmentId);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Adjunto eliminado correctamente"), Optional.empty()));
    }

    private SalesInvoiceAttachmentDTO toDto(SalesInvoiceAttachment a) {
        return SalesInvoiceAttachmentDTO.builder()
                .id(a.getId())
                .salesInvoiceId(a.getSalesInvoiceId())
                .fileName(a.getFileName())
                .mimeType(a.getMimeType())
                .fileSize(a.getFileSize())
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

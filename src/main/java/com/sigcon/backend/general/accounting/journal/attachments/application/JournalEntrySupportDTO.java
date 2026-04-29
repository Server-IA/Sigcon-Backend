package com.sigcon.backend.general.accounting.journal.attachments.application;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para un adjunto de asiento contable.
 * HU-CG-05A/B/C: no incluye el contenido binario (se descarga via /download).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JournalEntrySupportDTO {
    private Long id;
    private Long journalEntryId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String supportType;
    private String description;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}

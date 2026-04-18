package com.sigcon.backend.audit.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Registro inmutable de evento de auditoria (HU-AU-01)")
public class AuditLogDTO {
    private Long id;
    private LocalDateTime timestamp;
    private Long userId;
    private String userEmail;
    private String action;
    private String entityType;
    private Long entityId;
    private String module;
    private String severity;
    private String description;
    private String oldValues;
    private String newValues;
    private String ipAddress;
    private String userAgent;
    private String hash;
    private String previousHash;
    private Long journalEntryId;
}

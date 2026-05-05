package com.sigcon.backend.platform.audit.application;

import com.sigcon.backend.platform.audit.domain.model.PlatformAuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAuditLogDTO {
    private Long id;
    private LocalDateTime occurredAt;
    private Long actorUserId;
    private String actorEmail;
    private String action;
    private String targetType;
    private String targetId;
    private String targetLabel;
    private String payloadJson;
    private String remoteIp;
    private String userAgent;
    private Long durationMs;

    public static PlatformAuditLogDTO from(PlatformAuditLog e) {
        if (e == null) return null;
        return PlatformAuditLogDTO.builder()
                .id(e.getId())
                .occurredAt(e.getOccurredAt())
                .actorUserId(e.getActorUserId())
                .actorEmail(e.getActorEmail())
                .action(e.getAction())
                .targetType(e.getTargetType())
                .targetId(e.getTargetId())
                .targetLabel(e.getTargetLabel())
                .payloadJson(e.getPayloadJson())
                .remoteIp(e.getRemoteIp())
                .userAgent(e.getUserAgent())
                .durationMs(e.getDurationMs())
                .build();
    }
}

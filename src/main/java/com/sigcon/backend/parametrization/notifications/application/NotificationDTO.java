package com.sigcon.backend.parametrization.notifications.application;

import com.sigcon.backend.parametrization.notifications.domain.model.Notification;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private String type;
    private String module;
    private String eventKey;
    private String title;
    private String body;
    private String actionUrl;
    private Long sourceId;
    private String sourceType;
    private String severity;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Boolean read;

    public static NotificationDTO from(Notification n) {
        if (n == null) return null;
        return NotificationDTO.builder()
                .id(n.getId())
                .type(n.getType() != null ? n.getType().name() : null)
                .module(n.getModule())
                .eventKey(n.getEventKey())
                .title(n.getTitle())
                .body(n.getBody())
                .actionUrl(n.getActionUrl())
                .sourceId(n.getSourceId())
                .sourceType(n.getSourceType())
                .severity(n.getSeverity() != null ? n.getSeverity().name() : null)
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .expiresAt(n.getExpiresAt())
                .read(n.getReadAt() != null)
                .build();
    }
}

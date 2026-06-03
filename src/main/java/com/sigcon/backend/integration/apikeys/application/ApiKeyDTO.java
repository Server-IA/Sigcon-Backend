package com.sigcon.backend.integration.apikeys.application;

import com.sigcon.backend.integration.apikeys.domain.model.ApiKey;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PA-RF-28 (Pendientes PA): metadata de una API Key para los listados.
 * NUNCA incluye el hash ni la clave en texto plano.
 */
@Data
@Builder
@Schema(description = "Metadata de credencial AAEF (sin secreto)")
public class ApiKeyDTO {
    private Long id;
    private Long companyId;
    private String prefix;
    private String status;        // ACTIVE | REVOKED | EXPIRED
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private String revocationReason;
    private Long createdBy;

    public static ApiKeyDTO from(ApiKey k) {
        return ApiKeyDTO.builder()
                .id(k.getId())
                .companyId(k.getCompanyId())
                .prefix(k.getPrefix())
                .status(k.getStatus())
                .createdAt(k.getCreatedAt())
                .expiresAt(k.getExpiresAt())
                .lastUsedAt(k.getLastUsedAt())
                .revokedAt(k.getRevokedAt())
                .revocationReason(k.getRevocationReason())
                .createdBy(k.getCreatedBy())
                .build();
    }
}

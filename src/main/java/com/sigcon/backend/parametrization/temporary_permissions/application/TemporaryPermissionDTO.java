package com.sigcon.backend.parametrization.temporary_permissions.application;

import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporaryPermissionDTO {
    private Long id;
    private Long companyId;
    private Long userId;
    private Long permissionId;
    private String permissionCode;
    private Long grantedByUserId;
    private String grantedByEmail;
    private String justification;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private LocalDateTime revokedAt;
    private String revokedByEmail;
    private String revocationReason;
    private LocalDateTime createdAt;
    /** HU-PA-17 E1: dias restantes (solo si ACTIVE), negativo si ya vencio. */
    private Long daysRemaining;
    /** HU-PA-16 E3: si startDate > NOW indica "Programado". */
    private Boolean scheduled;

    public static TemporaryPermissionDTO from(TemporaryPermission t) {
        if (t == null) return null;
        LocalDateTime now = LocalDateTime.now();
        Long daysRem = null;
        Boolean sched = false;
        if (t.getStatus() == TemporaryPermission.Status.ACTIVE) {
            if (t.getStartDate() != null && t.getStartDate().isAfter(now)) {
                sched = true;
            }
            daysRem = ChronoUnit.DAYS.between(now, t.getEndDate());
        }
        return TemporaryPermissionDTO.builder()
                .id(t.getId())
                .companyId(t.getCompanyId())
                .userId(t.getUserId())
                .permissionId(t.getPermissionId())
                .permissionCode(t.getPermissionCode())
                .grantedByUserId(t.getGrantedByUserId())
                .grantedByEmail(t.getGrantedByEmail())
                .justification(t.getJustification())
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .status(t.getStatus() != null ? t.getStatus().name() : null)
                .revokedAt(t.getRevokedAt())
                .revokedByEmail(t.getRevokedByEmail())
                .revocationReason(t.getRevocationReason())
                .createdAt(t.getCreatedAt())
                .daysRemaining(daysRem)
                .scheduled(sched)
                .build();
    }
}

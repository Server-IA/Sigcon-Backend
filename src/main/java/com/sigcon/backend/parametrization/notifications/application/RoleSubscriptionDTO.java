package com.sigcon.backend.parametrization.notifications.application;

import com.sigcon.backend.parametrization.notifications.domain.model.RoleNotificationSubscription;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleSubscriptionDTO {
    private Long id;
    private Long roleId;
    private String eventKey;
    private Boolean enabled;
    private Integer thresholdDays;

    public static RoleSubscriptionDTO from(RoleNotificationSubscription s) {
        if (s == null) return null;
        return RoleSubscriptionDTO.builder()
                .id(s.getId())
                .roleId(s.getRoleId())
                .eventKey(s.getEventKey())
                .enabled(s.getEnabled())
                .thresholdDays(s.getThresholdDays())
                .build();
    }
}

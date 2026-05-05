package com.sigcon.backend.parametrization.notifications.application;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertRoleSubscriptionRequest {
    @NotBlank
    private String eventKey;
    private Boolean enabled;
    /** Solo aplica si el evento tiene supports_threshold=true. */
    private Integer thresholdDays;
}

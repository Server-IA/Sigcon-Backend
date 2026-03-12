package com.sigcon.backend.lists_accounting.cost_centers.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CostCenterStatus {
    ACTIVE,
    INACTIVE;

    @JsonCreator
    public static CostCenterStatus from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return CostCenterStatus.valueOf(value.toUpperCase());
    }
}

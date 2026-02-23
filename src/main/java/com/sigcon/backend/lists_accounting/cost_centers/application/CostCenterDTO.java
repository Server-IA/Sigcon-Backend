package com.sigcon.backend.lists_accounting.cost_centers.application;

import com.sigcon.backend.lists_accounting.cost_centers.domain.model.enums.CostCenterStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CostCenterDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private CostCenterStatus status;

    @NotNull(message = "El ID de empresa es obligatorio")
    private Long companyId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deletionReason;
}

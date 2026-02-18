package com.sigcon.backend.parametrization.parameters.application;

import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.parameters.domain.model.enums.CategoryParameter;
import com.sigcon.backend.parametrization.parameters.domain.model.enums.StatusParameter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class ParameterDTO {

    private Long id;
    private String name;
    private String value;
    private UserParameterDTO userParameter;
    private CategoryParameter category;
    private StatusParameter status;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime deleted_at;
}

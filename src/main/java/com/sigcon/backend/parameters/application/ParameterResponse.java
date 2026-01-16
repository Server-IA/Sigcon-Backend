package com.sigcon.backend.parameters.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ParameterResponse {
    private Long id;
    private Long parameterId;
    private String parameterName;
    private String parameterDescription;
    private String colorValue;
    private java.time.LocalDateTime creationDate;
    private java.time.LocalDateTime lastUpdateDate;
}

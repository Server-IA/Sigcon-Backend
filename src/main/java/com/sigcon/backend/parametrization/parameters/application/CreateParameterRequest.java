package com.sigcon.backend.parametrization.parameters.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateParameterRequest {
    private Long parameterId;
    private String colorValue; // Formato hexadecimal (ej: #FF5733 o FF5733)
}

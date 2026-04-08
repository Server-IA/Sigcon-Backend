package com.sigcon.backend.parametrization.resources.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class PaymentMethodDTO {
    private Long id;
    private String name;
    private String code;
}

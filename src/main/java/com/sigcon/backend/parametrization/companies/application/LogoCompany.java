package com.sigcon.backend.parametrization.companies.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoCompany {

    private String name;
    private String base64;
}

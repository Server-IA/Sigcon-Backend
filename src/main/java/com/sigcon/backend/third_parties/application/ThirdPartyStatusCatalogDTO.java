package com.sigcon.backend.third_parties.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyStatusCatalogDTO {
    private Long id;
    private String name;
}
package com.sigcon.backend.third_parties.third_parties.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyRoleCatalogDTO {
    private Long id;
    private String name;
}
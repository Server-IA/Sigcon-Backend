package com.sigcon.backend.parametrization.modules.application;

import com.sigcon.backend.parametrization.modules.domain.model.enums.Status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleDTO {

    private Long id;

    private String name;
    private String description;
    private String url;
    private String icon;
    private Integer position;
    private Status status;

}

package com.sigcon.backend.parametrization.users.application.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

import com.sigcon.backend.parametrization.users.domain.model.enums.TypePermits;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PermissionDTO {

    private String name;

    private Long menuId;

    private TypePermits type;

    private String description;


    private Set<Long> roleIds;

}

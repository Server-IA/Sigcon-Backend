package com.sigcon.backend.parametrization.users.application.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PermissionDTO {

    private String name;

    private Set<Long> roleIds;

}

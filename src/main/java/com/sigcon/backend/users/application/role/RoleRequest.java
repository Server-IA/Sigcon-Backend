package com.sigcon.backend.users.application.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleRequest {
    private Long id;
    private String name;
    private Set<Long> permissionIds;
    private String status;
}

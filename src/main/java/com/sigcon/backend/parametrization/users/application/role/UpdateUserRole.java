package com.sigcon.backend.parametrization.users.application.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserRole {

    private Long userId;
    private Long roleId;
    private String status;
}

package com.sigcon.backend.parametrization.users.application.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {

    private String name;
    private String lastname;
    private String email;
    private String password;

}

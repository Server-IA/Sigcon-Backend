package com.sigcon.backend.parametrization.users.application.auth;

import io.swagger.v3.oas.annotations.media.Schema;
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
    private String username;
    @Schema(description = "Contraseña del usuario", example = "123456")
    private String password;
    private String avatar;

    @Schema(description = "Username o email del usuario", example = "superadmin@gmail.com")
    private String usernameOrEmail;

}

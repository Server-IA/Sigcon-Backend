package com.sigcon.backend.parametrization.users.application.user;

import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private String name;
    private String lastname;
    private String email;
    private String password;
    private String role;
    private Status status;


    private Long id;
    private Set<String> roles;

}

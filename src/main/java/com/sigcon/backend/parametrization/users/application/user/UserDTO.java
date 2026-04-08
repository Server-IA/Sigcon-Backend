package com.sigcon.backend.parametrization.users.application.user;

import com.sigcon.backend.parametrization.companies.application.CompanyDTO;
import com.sigcon.backend.parametrization.parameters.application.ParameterDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;
    @NotBlank(message = "El apellido es obligatorio")
    private String lastname;
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no es válido")
    private String email;

    @NotBlank(message = "El usuario es obligatorio")
    private String username;

    private String password;
    private String avatar;
    private String role;
    private Status status;
    @NotNull(message = "La empresa es obligatoria")
    private Long companyId;
    private CompanyDTO company;
    private Long id;
    private Set<String> roles;
    private List<PermissionDTO> permissions;
    private List<ParameterDTO> parameters;
}

package com.sigcon.backend.parametrization.parameters.application;

import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.users.application.user.UserDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class UserParameterDTO {

    private Long id;
    private UserDTO user;
    private Long parameter_id;
    private String value;
    private ParameterDTO parameter;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime deleted_at;

}

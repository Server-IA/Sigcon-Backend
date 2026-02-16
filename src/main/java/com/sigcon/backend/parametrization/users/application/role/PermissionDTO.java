package com.sigcon.backend.parametrization.users.application.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

import com.sigcon.backend.parametrization.modules.application.ModuleDTO;
import com.sigcon.backend.parametrization.users.domain.model.enums.TypePermits;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PermissionDTO {

    private Long id;

    @NotBlank(message = "El nombre del permiso es obligatorio")
    private String name;

    @NotBlank(message = "El código del permiso es obligatorio")
    private String code;
    
    @NotNull(message = "El tipo de permiso es obligatorio")
    private TypePermits type;

    @NotNull(message = "El módulo es obligatorio")
    private Long moduleId;

    private ModuleDTO module;

    private String description;

    private Set<Long> roleIds;

}
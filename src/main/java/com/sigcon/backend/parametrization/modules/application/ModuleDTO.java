package com.sigcon.backend.parametrization.modules.application;

import java.util.List;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleDTO {

    private Long id;

    @NotBlank(message = "El nombre del modulo es requerido")
    @Size(min = 3, max = 50, message = "El nombre del modulo debe tener entre 3 y 50 caracteres")
    private String name;
    private String description;
    @NotBlank(message = "La url del modulo es requerida")
    @Size(min = 3, max = 50, message = "La url del modulo debe tener entre 3 y 50 caracteres")
    private String url;
    private String icon;

    @NotNull(message = "La posicion del modulo es requerida")   
    @Min(value = 1, message = "La posicion del modulo debe ser mayor a 0")
    private Integer position;
    private ModelStatus status;

    private List<Menu> menus;

}

package com.sigcon.backend.parametrization.modules.domain.model;

import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

@Entity
@Table(name = "modules")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @Nullable
    private String description;

    @NotBlank(message = "La URL es obligatoria")
    private String url;

    @Nullable
    private String icon;

    @NotNull(message = "La posición es obligatoria")
    @Min(value = 1, message = "La posición debe ser mayor a 0")
    private Integer position = 1;

    @Builder.Default
    private ModelStatus status = ModelStatus.ACTIVE;

}

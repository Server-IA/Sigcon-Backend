package com.sigcon.backend.parametrization.modules.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

public class ModuleEntity {

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

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private LocalDateTime created_at = LocalDateTime.now();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private LocalDateTime updated_at = LocalDateTime.now();

    @Column(name = "deleted_at")
    @Temporal(TemporalType.TIMESTAMP)
    @Nullable
    private LocalDateTime deleted_at;

}

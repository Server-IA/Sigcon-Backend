package com.sigcon.backend.parametrization.parameters.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "parameters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Parameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @Column(length = 500)
    private String description;

    // HU-25: valor del parámetro (ej: #FF5733, DARK, etc.)
    @Column(nullable = false)
    @NotBlank(message = "El valor es obligatorio")
    private String value;

    // HU-25: categoría (ej: Colores, Tema, etc.)
    @Column(nullable = false)
    @NotBlank(message = "La categoría es obligatoria")
    private String category;

    // Estado (activo/inactivo)
    @Column(nullable = false)
    @NotNull(message = "El estado es obligatorio")
    private Boolean active;

    @CreationTimestamp
    private LocalDateTime creationDate;

    @UpdateTimestamp
    private LocalDateTime lastUpdateDate;

    // HU-28: eliminación lógica
    private LocalDateTime deletedAt;
}

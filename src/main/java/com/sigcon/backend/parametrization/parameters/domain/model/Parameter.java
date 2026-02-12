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

import com.sigcon.backend.parametrization.parameters.domain.model.enums.CategoryParameter;
import com.sigcon.backend.parametrization.parameters.domain.model.enums.StatusParameter;

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
    @NotNull(message = "El nombre es obligatorio")
    private String name;

    @Column(length = 500)
    private String description;

    // HU-25: categoría (ej: Colores, Tema, etc.)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "La categoría es obligatoria")
    private CategoryParameter category;

    // Estado (activo/inactivo)
    @Column(nullable = false)
    @NotNull(message = "El estado es obligatorio")
    @Enumerated(EnumType.STRING)
    private StatusParameter status = StatusParameter.ACTIVE;

    @CreationTimestamp
    private LocalDateTime created_at;

    @UpdateTimestamp
    private LocalDateTime updated_at;

    // HU-28: eliminación lógica
    private LocalDateTime deleted_at;

    @PrePersist
    protected void onCreate() {
        this.created_at = LocalDateTime.now();
        this.updated_at = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updated_at = LocalDateTime.now();
    }

    @PreRemove
    protected void onDelete() {
        this.deleted_at = LocalDateTime.now();
    }
}

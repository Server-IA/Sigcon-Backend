package com.sigcon.backend.parametrization.parameters.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import com.sigcon.backend.parametrization.parameters.domain.model.enums.CategoryParameter;
import com.sigcon.backend.parametrization.parameters.domain.model.enums.StatusParameter;

import java.time.LocalDateTime;

@Entity
@Table(name = "parameters")
@SQLDelete(sql = "UPDATE parameters SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Parameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotNull(message = "El nombre es obligatorio")
    private String name;

    @Column(nullable = true)
    private String value;

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
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // HU-28: eliminación lógica
    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreRemove
    protected void onDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}

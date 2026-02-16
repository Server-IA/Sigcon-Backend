package com.sigcon.backend.parametrization.users.domain.model;

import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.modules.domain.model.Module;

import com.sigcon.backend.parametrization.users.domain.model.enums.TypePermits;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "module_id")
    private Module module;

    @Column(unique = true)
    @NotNull(message = "El nombre del permiso es obligatorio")
    private String name;

    @Column(unique = true, nullable = false)
    @NotNull(message = "El código del permiso es obligatorio")
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "El tipo de permiso es obligatorio")
    private TypePermits type;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDateTime created_at;

    @Column(nullable = false)
    private LocalDateTime updated_at;

    @Column(nullable = true)
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
}

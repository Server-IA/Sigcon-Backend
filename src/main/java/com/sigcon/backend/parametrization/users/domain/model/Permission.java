package com.sigcon.backend.parametrization.users.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;

import com.sigcon.backend.parametrization.users.domain.model.enums.TypePermits;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@SQLDelete(sql = "UPDATE permissions SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")

@Getter
@Setter

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private ModuleEntity module;

    // NOTA: unicidad de name y code la garantiza un UNIQUE parcial que respeta
    // soft-delete (ver V9-O__permissions_unique_partial.sql). Si aqui se pusiera
    // unique=true, Hibernate crearia un UNIQUE GLOBAL que incluye registros con
    // deleted_at != NULL y choca con re-seeds/renames en Dokploy.
    @NotNull(message = "El nombre del permiso es obligatorio")
    private String name;

    @Column(nullable = false)
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

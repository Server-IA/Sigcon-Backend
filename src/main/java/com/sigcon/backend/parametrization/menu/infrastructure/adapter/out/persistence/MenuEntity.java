package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;
import org.hibernate.validator.constraints.UniqueElements;
import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

import io.micrometer.common.lang.Nullable;

@Entity
@Table(name = "menus")
@SQLDelete(sql = "UPDATE menus SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class MenuEntity {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "label")
    @NotBlank(message = "El Nombre del menu es obligatorio")
    private String label;
    
    @Column(name = "icon")
    private String icon;
    
    @Column(name = "path")
    @NotBlank(message = "La URL es obligatoria")
    private String path;
    
    @Column(name = "menu_order")
    @NotNull(message = "La posición es obligatoria")
    @Min(value = 1, message = "La posición debe ser mayor a 0")
    private Integer menuOrder = 1;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = true)
    private MenuEntity parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private ModuleEntity module;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private MenuStatus status = MenuStatus.ACTIVE   ;

    @Column(name = "component")
    private String component;

    @Column(name = "visible")
    private Boolean visible = true;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "menu_type", length = 50)
    private String menuType;

    /**
     * QA Bloque AX (Bug #3, 2026-05-17): code del permiso requerido para mostrar
     * este menu en el sidebar. NULL = publico. Seedeado en V9-Zzzzf con mapping
     * component -> permission_code (ej. CENTROS_COSTO -> CFG.CENTROS_COSTO.VER).
     */
    @Column(name = "required_permission_code", length = 120)
    private String requiredPermissionCode;

    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    @Temporal(TemporalType.TIMESTAMP)
    @Nullable
    private LocalDateTime deletedAt;




}

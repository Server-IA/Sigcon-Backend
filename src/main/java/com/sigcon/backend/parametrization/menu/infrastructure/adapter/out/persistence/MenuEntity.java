package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.validator.constraints.UniqueElements;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

@Entity
@Table(name = "menus")
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
    
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "module_id")
    @NotNull(message = "El modulo es obligatorio")
    private Long moduleId;

    @Column(name = "status")
    private MenuStatus status = MenuStatus.ACTIVE   ;

    @Column(name = "component", unique = true)
    @NotBlank(message = "El componente es obligatorio")
    private String component;
}

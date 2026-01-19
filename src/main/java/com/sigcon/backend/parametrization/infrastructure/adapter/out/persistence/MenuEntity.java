package com.sigcon.backend.parametrization.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "menus")
public class MenuEntity {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "label")
    private String label;
    
    @Column(name = "icon")
    private String icon;
    
    @Column(name = "path")
    private String path;
    
    @Column(name = "menu_order")
    private Integer menuOrder;
    
    @Column(name = "parent_id")
    private Long parentId;
    
    @Column(name = "active")
    private Boolean active;
    
    // Constructor sin argumentos requerido por JPA
    public MenuEntity() {
    }
    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public String getIcon() {
        return icon;
    }
    public void setIcon(String icon) {
        this.icon = icon;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public Integer getMenuOrder() {
        return menuOrder;
    }
    public void setMenuOrder(Integer menuOrder) {
        this.menuOrder = menuOrder;
    }
    public Long getParentId() {
        return parentId;
    }
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
    public Boolean getActive() {
        return active;
    }
    public void setActive(Boolean active) {
        this.active = active;
    }

}

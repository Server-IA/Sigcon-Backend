package com.sigcon.backend.domain.menu;

public class Menu {

    private Long id;
    private String label;
    private String icon;
    private String path;
    private Integer order;
    private Long parentId;
    private Boolean active;

    public Menu(Long id, String label, String icon, String path, Integer order, Long parentId, Boolean active) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.path = path;
        this.order = order;
        this.parentId = parentId;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String getPath() {
        return path;
    }

    public Integer getOrder() {
        return order;
    }

    public Long getParentId() {
        return parentId;
    }

    public Boolean getActive() {
        return active;
    }

    
    
}

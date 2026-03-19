package com.sigcon.backend.parametrization.menu.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.mapping.Array;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;
import com.sigcon.backend.utils.DataTableRequest;

public interface MenuRepositoryPort {

    Page<MenuEntity> findAll(Specification<MenuEntity> spec, Pageable pageable);

    Map<Long, List<Menu>> findMenusByModuleIdAndRoles(Long moduleId, List<Long> roles, boolean isAdmin);
    MenuEntity saveMenu(MenuEntity menuEntity);
    Optional<MenuEntity> findMenuByLabel(String label);
    Optional<MenuEntity> findMenuByPath(String path);
    Optional<MenuEntity> findMenuByComponent(String component);
    Optional<Menu> findMenuById(Long id);

    MenuEntity findById(Long id);
    
    Map<Long, List<Menu>> findMenusByParentId(Long parentId);

    MenuEntity updateMenu(MenuEntity menuEntity);
    MenuEntity deleteMenu(Long id);
}

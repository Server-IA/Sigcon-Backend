package com.sigcon.backend.parametrization.menu.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuDataTableRequest;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

public interface MenuRepositoryPort {

    Page<Menu> findMenusAllAndDeletedAtIsNull(Pageable pageable);

    Page<Menu> findMenusAllFiltersAndDeletedAtIsNull(
        String label,
        String description,
        String url,
        String icon,
        Integer position,
        MenuStatus menuStatus,
        Long moduleId,
        Long parentId,
        Pageable pageable);

    boolean noFilters(MenuDataTableRequest request);
    Map<Long, List<Menu>> findMenusByModuleId(Long moduleId);
    MenuEntity saveMenu(MenuEntity menuEntity);
    Optional<MenuEntity> findMenuByLabel(String label);
    Optional<Menu> findMenuById(Long id);

    MenuEntity findById(Long id);
    
    Map<Long, List<Menu>> findMenusByParentId(Long parentId);

    MenuEntity updateMenu(MenuEntity menuEntity);
}

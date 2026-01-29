package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

import java.util.List;

public interface SpringDataMenuRepository extends JpaRepository<MenuEntity, Long> {
    List<MenuEntity> findByOrderByMenuOrderAsc();
    List<MenuEntity> findByModuleIdAndStatusOrderByMenuOrderAsc(Long moduleId, MenuStatus status);
}
    
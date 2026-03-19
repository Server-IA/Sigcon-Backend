package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;
import com.sigcon.backend.utils.DataTableRequest;

import java.util.List;
import java.util.Optional;

public interface SpringDataMenuRepository extends JpaRepository<MenuEntity, Long>, JpaSpecificationExecutor<MenuEntity> {
    Page<MenuEntity> findByOrderByMenuOrderAsc(Pageable pageable);

    @Query(value = """
      SELECT m.*
      FROM menus m
      LEFT JOIN menu_permissions mp on mp.menu_id = m.id
      LEFT JOIN roles r on mp.role_id = r.id 
      WHERE m.module_id = :moduleId AND m.status = :status AND m.deleted_at IS NULL
      AND (r.id = :roles OR :isAdmin = true)
      AND mp.deleted_at IS NULL
      ORDER BY m.menu_order ASC
      """, nativeQuery = true)
    List<MenuEntity> findMenusByModuleIdAndRoles(Long moduleId, List<Long> roles, String status, boolean isAdmin);

    @Query(value = """
        SELECT * FROM menus m WHERE m.label = :label AND m.deleted_at IS NULL
        """, nativeQuery = true)
    Optional<MenuEntity> findByLabel(String label);

    Optional<MenuEntity> findById(Long id);

    @Query(value = """
        SELECT m.*
          FROM menus m
          WHERE m.parent_id = :parentId AND m.status = :status AND m.deleted_at IS NULL
        ORDER BY m.menu_order ASC
        """, nativeQuery = true)
    List<MenuEntity> findMenusByParentId(Long parentId, String status);

    @Query(value = """
        SELECT m.*
          FROM menus m
          WHERE m.path = :path AND m.deleted_at IS NULL
        """, nativeQuery = true)
    Optional<MenuEntity> findMenusByPath(String path);

    @Query(value = """
        SELECT m.*
          FROM menus m
          WHERE m.component = :component AND m.deleted_at IS NULL
        """, nativeQuery = true)
    Optional<MenuEntity> findMenusByComponent(String component);

    @Override
    Page<MenuEntity> findAll(Specification<MenuEntity> spec, Pageable pageable);

    // Page<MenuEntity> findAll(Specification<MenuEntity> spec, Pageable pageable);
}
    
package com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.parametrization.menu.Menu;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

import java.util.List;
import java.util.Optional;

public interface SpringDataMenuRepository extends JpaRepository<MenuEntity, Long> {
    Page<MenuEntity> findByOrderByMenuOrderAsc(Pageable pageable);

    @Query(value = """
        SELECT m.*
          FROM menus m
          WHERE m.module_id = :moduleId AND m.status = :status AND m.deleted_at IS NULL
        ORDER BY m.menu_order ASC
        """, nativeQuery = true)
    List<MenuEntity> findMenusByModule(Long moduleId, MenuStatus status);

    Optional<MenuEntity> findByLabel(String label);
    Optional<MenuEntity> findById(Long id);

    @Query(value = """
        SELECT * FROM menus m WHERE m.deleted_at IS NULL
        """, countQuery = """
        SELECT COUNT(*) FROM menus m WHERE m.deleted_at IS NULL
        """, nativeQuery = true)
    Page<MenuEntity> findMenusAllAndDeletedAtIsNull(Pageable pageable);

    @Query(value = """
        SELECT * FROM menus m
        WHERE (:label IS NULL OR m.label ILIKE CONCAT('%', :label, '%'))
          AND (:description IS NULL OR m.label ILIKE CONCAT('%', :description, '%'))
          AND (:url IS NULL OR m.path ILIKE CONCAT('%', :url, '%'))
          AND (:icon IS NULL OR m.icon ILIKE CONCAT('%', :icon, '%'))
          AND (:position IS NULL OR m.menu_order = :position)
          AND (:menuStatus IS NULL OR m.status = :menuStatus)
          AND (:moduleId IS NULL OR m.module_id = :moduleId)
          AND (:parentId IS NULL OR m.parent_id = :parentId)
          AND m.deleted_at IS NULL
        """, countQuery = """
        SELECT COUNT(*) FROM menus m
        WHERE (:label IS NULL OR m.label ILIKE CONCAT('%', :label, '%'))
          AND (:description IS NULL OR m.label ILIKE CONCAT('%', :description, '%'))
          AND (:url IS NULL OR m.path ILIKE CONCAT('%', :url, '%'))
          AND (:icon IS NULL OR m.icon ILIKE CONCAT('%', :icon, '%'))
          AND (:position IS NULL OR m.menu_order = :position)
          AND (:menuStatus IS NULL OR m.status = :menuStatus)
          AND (:moduleId IS NULL OR m.module_id = :moduleId)
          AND (:parentId IS NULL OR m.parent_id = :parentId)
          AND m.deleted_at IS NULL
        """, nativeQuery = true)
    Page<MenuEntity> findMenusAllFiltersAndDeletedAtIsNull(
        String label,
        String description,
        String url,
        String icon,
        Integer position,
        MenuStatus menuStatus,
        Long moduleId,
        Long parentId,
        Pageable pageable);

    @Query(value = """
        SELECT m.*
          FROM menus m
          WHERE m.parent_id = :parentId AND m.status = :status AND m.deleted_at IS NULL
        ORDER BY m.menu_order ASC
        """, nativeQuery = true)
    List<MenuEntity> findMenusByParentId(Long parentId, MenuStatus status);

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
}
    
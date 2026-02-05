package com.sigcon.backend.parametrization.modules.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigcon.backend.parametrization.modules.domain.model.Module;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ModuleRepository extends JpaRepository<Module, Long> {

    Optional<Module> findByName(String name);
    List<Module> findAllByStatus(ModelStatus status);

    @Query("""
        SELECT m
        FROM Module m
        WHERE (:name IS NULL OR m.name ILIKE CONCAT('%', :name, '%'))
          AND (:description IS NULL OR m.description ILIKE CONCAT('%', :description, '%'))
          AND (:url IS NULL OR m.url ILIKE CONCAT('%', :url, '%'))
          AND (:icon IS NULL OR m.icon ILIKE CONCAT('%', :icon, '%'))
          AND (:position IS NULL OR m.position = :position)
          AND (:status IS NULL OR m.status = :status)
          AND (m.deleted_at IS NULL)
    """)

    Page<Module> searchModules(
        String name,
        String description,
        String url,
        String icon,
        Integer position,
        ModelStatus status,
        Pageable pageable
    );
    
    @Query(value = """
        SELECT DISTINCT m.*
        FROM modules m
        WHERE m.status = :status
          AND m.deleted_at IS NULL
          AND EXISTS (
                SELECT 1
                FROM menus menu
                WHERE menu.module_id = m.id
                    AND menu.status = :menuStatus
          )
        ORDER BY m.position ASC
    """, nativeQuery = true)
    List<Module> findActiveModulesWithActiveMenus(ModelStatus status, MenuStatus menuStatus);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Query(value = "SELECT m.* FROM modules m WHERE m.deleted_at IS NULL", nativeQuery = true)
    Page<Module> findAllAndDeletedAtIsNull(Pageable pageable);
    
}

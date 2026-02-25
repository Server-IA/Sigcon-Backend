package com.sigcon.backend.parametrization.modules.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ModuleRepository extends JpaRepository<ModuleEntity, Long>, JpaSpecificationExecutor<ModuleEntity> {

    Optional<ModuleEntity> findByName(String name);

    List<ModuleEntity> findAllByStatus(ModelStatus status);

    @Query(value = """
        SELECT *
        FROM modules m
        WHERE (NULLIF(:name, '') IS NULL OR m.name ILIKE CONCAT('%', :name, '%'))
        AND (NULLIF(:description, '') IS NULL OR m.description ILIKE CONCAT('%', :description, '%'))
        AND (NULLIF(:url, '') IS NULL OR m.url ILIKE CONCAT('%', :url, '%'))
        AND (NULLIF(:icon, '') IS NULL OR m.icon ILIKE CONCAT('%', :icon, '%'))
        AND (:position IS NULL OR m.position = :position)
        AND (:status IS NULL OR m.status = :status)
        AND m.deleted_at IS NULL
    """, nativeQuery = true)

    Page<ModuleEntity> searchModules(
            String name,
            String description,
            String url,
            String icon,
            Integer position,
            ModelStatus status,
            Pageable pageable);

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
                AND menu.deleted_at IS NULL
          )
        ORDER BY m.position ASC
    """, nativeQuery = true)
    List<ModuleEntity> findActiveModulesWithActiveMenus(String status, String menuStatus);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Query(value = "SELECT m.* FROM modules m WHERE m.deleted_at IS NULL", nativeQuery = true)
    Page<ModuleEntity> findAllAndDeletedAtIsNull(Pageable pageable);

    boolean existsByNameAndDeletedAtIsNull(String name);
    boolean existsByUrlAndDeletedAtIsNull(String url);

}

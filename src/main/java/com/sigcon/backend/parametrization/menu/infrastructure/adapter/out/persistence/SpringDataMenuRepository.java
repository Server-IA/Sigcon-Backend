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

    /**
     * Devuelve los menus activos de un modulo visibles para un set de roles.
     * <p>Semantica de visibilidad:
     * <ul>
     *   <li>Si {@code isAdmin=true}: devuelve todos los menus activos.</li>
     *   <li>Si un menu NO tiene filas en {@code menu_permissions}: es publico
     *       para cualquier usuario autenticado (compat hacia atras, ya que hoy
     *       la tabla esta vacia).</li>
     *   <li>Si un menu tiene filas en {@code menu_permissions}: solo los roles
     *       presentes en esa tabla lo ven.</li>
     * </ul>
     */
    @Query(value = """
      SELECT m.*
        FROM menus m
       WHERE m.module_id = :moduleId
         AND m.status = :status
         AND m.deleted_at IS NULL
         AND (
              :isAdmin = true
              OR NOT EXISTS (
                  SELECT 1 FROM menu_permissions mp
                   WHERE mp.menu_id = m.id AND mp.deleted_at IS NULL
              )
              OR EXISTS (
                  SELECT 1 FROM menu_permissions mp
                   WHERE mp.menu_id = m.id
                     AND mp.deleted_at IS NULL
                     AND mp.role_id IN (:roles)
              )
         )
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

    boolean existsByParent_IdAndDeletedAtIsNull(Long parentId);

    /**
     * PA-RF-17: verifica si un modulo tiene menus activos asociados.
     * Usado para evitar eliminar un modulo que todavia tiene menus vivos.
     */
    boolean existsByModule_IdAndDeletedAtIsNull(Long moduleId);
}
    
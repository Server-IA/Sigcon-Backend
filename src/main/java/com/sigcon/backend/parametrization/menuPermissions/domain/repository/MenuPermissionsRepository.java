package com.sigcon.backend.parametrization.menuPermissions.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menuPermissions.domain.model.MenuPermissionsEntity;
import com.sigcon.backend.parametrization.users.domain.model.Role;

public interface MenuPermissionsRepository extends JpaRepository<MenuPermissionsEntity, Long>, JpaSpecificationExecutor<MenuPermissionsEntity> {

    Optional<MenuPermissionsEntity> findByMenuIdAndRoleIdAndDeletedAtIsNull(Long menuId, Long roleId);
    Optional<MenuPermissionsEntity> findByMenuIdAndRoleIdAndIdNotAndDeletedAtIsNull(Long menuId, Long roleId, Long idNot);
    Optional<MenuPermissionsEntity> findByMenuAndRoleAndDeletedAtIsNull(MenuEntity menu, Role role);

    /** Verifica si un menu tiene permisos activos asociados (para validar antes de eliminar) */
    boolean existsByMenuIdAndDeletedAtIsNull(Long menuId);
}

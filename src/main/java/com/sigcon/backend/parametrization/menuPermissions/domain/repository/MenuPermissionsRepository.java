package com.sigcon.backend.parametrization.menuPermissions.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.parametrization.menuPermissions.domain.model.MenuPermissionsEntity;

public interface MenuPermissionsRepository extends JpaRepository<MenuPermissionsEntity, Long>, JpaSpecificationExecutor<MenuPermissionsEntity> {

    Optional<MenuPermissionsEntity> findByMenuIdAndRoleIdAndDeletedAtIsNull(Long menuId, Long roleId);
    Optional<MenuPermissionsEntity> findByMenuIdAndRoleIdAndIdNotAndDeletedAtIsNull(Long menuId, Long roleId, Long idNot);
}

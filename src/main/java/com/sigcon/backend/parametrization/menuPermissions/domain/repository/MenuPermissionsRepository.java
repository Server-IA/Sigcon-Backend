package com.sigcon.backend.parametrization.menuPermissions.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.parametrization.menuPermissions.domain.model.MenuPermissionsEntity;

public interface MenuPermissionsRepository extends JpaRepository<MenuPermissionsEntity, Long>, JpaSpecificationExecutor<MenuPermissionsEntity> {

}

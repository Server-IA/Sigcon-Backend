package com.sigcon.backend.parametrization.users.domain.repository;

import com.sigcon.backend.parametrization.users.domain.model.Permission;
import com.sigcon.backend.parametrization.users.domain.model.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {
    Optional<Permission> findByName(String name);

    @Query(value = """
        SELECT p.*
        FROM permissions p
        WHERE p.deleted_at IS NULL
    """, nativeQuery = true)
    Page<Permission> findAllAndDeletedAtIsNull(Pageable pageable);

    @Query(value = """
        SELECT DISTINCT p.*
        FROM permissions p
        LEFT JOIN roles_permissions rp ON p.id = rp.permission_id
        LEFT JOIN roles r ON rp.role_id = r.id
        LEFT JOIN users_roles ur ON r.id = ur.role_id
        WHERE p.deleted_at IS NULL
        AND ur.user_id = :userID
    """, nativeQuery = true)
    List<Permission> findByUserID(Long userID);

}

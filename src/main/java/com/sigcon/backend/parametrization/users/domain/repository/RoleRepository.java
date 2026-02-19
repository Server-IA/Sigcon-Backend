package com.sigcon.backend.parametrization.users.domain.repository;

import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role,Long>, JpaSpecificationExecutor<Role> {
    Optional<Role> findByName(String name);

    Page<Role> findByStatus(Status status, Pageable pageable);

    Page<Role> findByNameContainingIgnoreCaseAndStatus(
            String name,
            Status status,
            Pageable pageable
    );

    @Query(value = """
        SELECT r.*
        FROM roles_permissions rp
        LEFT JOIN roles r ON rp.role_id = r.id
        WHERE rp.permission_id = :permissionId
    """, nativeQuery = true)
    List<Role> findAllByPermissions_Id(Long permissionId);

    Optional<Role> findByNameAndDeletedAtIsNull(String name);
}

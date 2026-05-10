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

    /**
     * HU-PA-04 E3 / Bug 4 (2026-05-09): unicidad por (companyId, name) en
     * lugar de unicidad global. Se usa en createRole para validar que no
     * exista otro rol con el mismo nombre DENTRO DEL TENANT.
     * companyId == null busca en roles globales del sistema.
     */
    Optional<Role> findByNameIgnoreCaseAndCompanyIdAndDeletedAtIsNull(String name, Long companyId);

    /**
     * HU-PA-03 E1: lista los roles visibles para un tenant. Incluye:
     *  - Los roles del propio tenant (companyId = X).
     * NO incluye roles globales (PLATFORM_ADMIN, ADMIN, USER) por el
     * aislamiento estricto que pide HU-PA-03 E3.
     */
    @Query("SELECT r FROM Role r WHERE r.deletedAt IS NULL AND r.companyId = :companyId")
    List<Role> findAllForTenant(Long companyId);

    /** Conteo de usuarios asignados al rol (HU-PA-03 E1, columna #usuarios). */
    @Query(value = "SELECT COUNT(*) FROM users_roles ur " +
                   "JOIN users u ON u.id = ur.user_id " +
                   "WHERE ur.role_id = :roleId AND u.deleted_at IS NULL", nativeQuery = true)
    Long countActiveUsersByRoleId(Long roleId);
}

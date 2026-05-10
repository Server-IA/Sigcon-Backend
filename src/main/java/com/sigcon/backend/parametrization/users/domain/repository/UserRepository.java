package com.sigcon.backend.parametrization.users.domain.repository;

import com.sigcon.backend.parametrization.users.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByRoles_Name(String roleName);

    /**
     * HU-PA-06 E2 (Bloque PA Bug 10, 2026-05-09): lista usuarios asignados a un rol
     * concreto (por id), no por name. En multi-tenant cada empresa tiene su rol
     * id propio, asi que filtrar por nombre devolvia falsos positivos cross-tenant.
     */
    List<User> findAllByRoles_IdAndDeletedAtIsNull(Long roleId);

    /** HU-PA-06 E5: cuenta usuarios activos con un rol especifico (cualquier company). */
    long countByRoles_IdAndDeletedAtIsNull(Long roleId);

    /**
     * HU-PA-06 E5: cuenta usuarios cuyo UNICO rol activo es el dado.
     * Usado para determinar si el usuario perderia acceso al sistema (HU-PA-06 E3).
     */
    @org.springframework.data.jpa.repository.Query(value =
        "SELECT COUNT(*) FROM users u " +
        "WHERE u.deleted_at IS NULL " +
        "  AND u.id IN (SELECT user_id FROM users_roles WHERE role_id = :roleId) " +
        "  AND (SELECT COUNT(*) FROM users_roles ur2 JOIN roles r2 ON r2.id = ur2.role_id " +
        "       WHERE ur2.user_id = u.id AND r2.deleted_at IS NULL) = 1",
        nativeQuery = true)
    long countUsersWithOnlyThisRoleActive(@org.springframework.data.repository.query.Param("roleId") Long roleId);

    /**
     * HU-PA-06 E5: cuenta admins activos con rol ADMIN_EMPRESA en una empresa
     * (excluyendo el rol que se intenta eliminar). Si es 0 y el rol a eliminar
     * es ADMIN_EMPRESA, debe rechazarse para no dejar la empresa sin admin.
     */
    @org.springframework.data.jpa.repository.Query(value =
        "SELECT COUNT(DISTINCT u.id) FROM users u " +
        "JOIN users_roles ur ON ur.user_id = u.id " +
        "JOIN roles r ON r.id = ur.role_id " +
        "WHERE u.company_id = :companyId AND u.deleted_at IS NULL " +
        "  AND r.deleted_at IS NULL AND UPPER(r.name) = 'ADMIN_EMPRESA' " +
        "  AND r.id <> :excludeRoleId",
        nativeQuery = true)
    long countOtherAdminEmpresaInCompany(@org.springframework.data.repository.query.Param("companyId") Long companyId,
                                          @org.springframework.data.repository.query.Param("excludeRoleId") Long excludeRoleId);
    boolean existsByEmailAndDeletedAtIsNull(String email);

    /**
     * HU-PA-09 E4 (Bloque PA Bug 22, 2026-05-09): valida unicidad global del email
     * al actualizar, excluyendo el id del usuario que se esta editando. Si retorna
     * true significa que OTRO usuario (incluso de otra empresa o de plataforma) ya
     * tiene ese email, por lo que el cambio debe rechazarse.
     */
    boolean existsByEmailAndIdNotAndDeletedAtIsNull(String email, Long id);

    Optional<User> findByUsernameOrEmail(String username, String email);
}

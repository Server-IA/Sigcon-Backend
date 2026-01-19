package com.sigcon.backend.users.domain.repository;

import com.sigcon.backend.users.domain.model.User;
import com.sigcon.backend.users.domain.model.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByRoles_Name(String roleName);

    @Query("""
        SELECT DISTINCT u
        FROM User u
        LEFT JOIN u.roles r
        WHERE (:name IS NULL OR u.name ILIKE CONCAT('%', :name, '%'))
          AND (:lastname IS NULL OR u.lastname ILIKE CONCAT('%', :lastname, '%'))
          AND (:email IS NULL OR u.email ILIKE CONCAT('%', :email, '%'))
          AND (:role IS NULL OR r.name = :role)
          AND (:status IS NULL OR u.status = :status)
    """)
    Page<User> searchUsers(
            @Param("name") String name,
            @Param("lastname") String lastname,
            @Param("email") String email,
            @Param("role") String role,
            @Param("status") Status status,
            Pageable pageable
    );








}

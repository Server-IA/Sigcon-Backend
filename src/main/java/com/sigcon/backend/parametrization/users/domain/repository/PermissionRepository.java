package com.sigcon.backend.parametrization.users.domain.repository;

import com.sigcon.backend.parametrization.users.domain.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByName(String name);
}

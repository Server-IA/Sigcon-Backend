package com.sigcon.backend.users.domain.repository;

import com.sigcon.backend.users.domain.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role,Long> {
    Optional<Role> findByName(String name);
    Page<Role> findByNameContainingIgnoreCase(String name, Pageable pageable);
}

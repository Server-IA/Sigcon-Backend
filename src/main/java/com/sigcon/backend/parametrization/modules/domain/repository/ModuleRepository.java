package com.sigcon.backend.parametrization.modules.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigcon.backend.parametrization.modules.domain.model.Module;
import com.sigcon.backend.parametrization.modules.domain.model.enums.Status;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ModuleRepository extends JpaRepository<Module, Long> {

    Optional<Module> findByName(String name);
    List<Module> findAllByStatus(Status status);

    @Query("""
        SELECT m
        FROM Module m
        WHERE (:name IS NULL OR m.name ILIKE CONCAT('%', :name, '%'))
          AND (:description IS NULL OR m.description ILIKE CONCAT('%', :description, '%'))
          AND (:url IS NULL OR m.url ILIKE CONCAT('%', :url, '%'))
          AND (:icon IS NULL OR m.icon ILIKE CONCAT('%', :icon, '%'))
          AND (:position IS NULL OR m.position = :position)
          AND (:status IS NULL OR m.status = :status)
    """)

    Page<Module> searchModules(
        @Param("name") String name,
        @Param("description") String description,
        @Param("url") String url,
        @Param("icon") String icon,
        @Param("position") Integer position,
        @Param("status") Status status,
        Pageable pageable
    );
    boolean existsByName(String name);
}

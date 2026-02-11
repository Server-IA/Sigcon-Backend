package com.sigcon.backend.parametrization.parameters.domain.repository;

import com.sigcon.backend.parametrization.parameters.domain.model.Parameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ParameterRepository extends JpaRepository<Parameter, Long> {

    Optional<Parameter> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    // Solo activos y no eliminados lógicamente
    List<Parameter> findByActiveTrueAndDeletedAtIsNull();

    Page<Parameter> findByActiveTrueAndDeletedAtIsNull(Pageable pageable);

    // Listado general (no eliminado)
    @Query("""
                SELECT p
                FROM Parameter p
                WHERE p.deletedAt IS NULL
            """)
    Page<Parameter> findAllNotDeleted(Pageable pageable);

    // Búsqueda con filtros opcionales (HU-25)
    @Query("""
                SELECT p
                FROM Parameter p
                WHERE (:name IS NULL OR p.name ILIKE CONCAT('%', :name, '%'))
                  AND (:value IS NULL OR p.value ILIKE CONCAT('%', :value, '%'))
                  AND (:category IS NULL OR p.category ILIKE CONCAT('%', :category, '%'))
                  AND (:active IS NULL OR p.active = :active)
                  AND p.deletedAt IS NULL
            """)
    Page<Parameter> searchParameters(
            String name,
            String value,
            String category,
            Boolean active,
            Pageable pageable);
}

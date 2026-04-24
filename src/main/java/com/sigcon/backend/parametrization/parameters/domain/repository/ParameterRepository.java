package com.sigcon.backend.parametrization.parameters.domain.repository;

import com.sigcon.backend.parametrization.parameters.domain.model.Parameter;
import com.sigcon.backend.parametrization.parameters.domain.model.enums.CategoryParameter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ParameterRepository extends JpaRepository<Parameter, Long>, JpaSpecificationExecutor<Parameter> {

    Optional<Parameter> findByName(String name);

    boolean existsByName(String name);
    boolean existsByNameAndCategoryAndDeletedAtIsNull(String name, CategoryParameter category);

    boolean existsByNameAndIdNot(String name, Long id);
    boolean existsByNameAndCategoryAndIdNot(String name, CategoryParameter category, Long id);

    List<Parameter> findByCategoryAndDeletedAtIsNull(CategoryParameter category);
    Optional<Parameter> findByNameAndDeletedAtIsNull(String name);

    /**
     * Busca un parametro SIN aplicar el tenant filter. Usado para configuracion
     * global de plataforma (ej. AGROFUSION_API_KEY unica cross-empresa).
     *
     * <p>Devuelve el primero encontrado en la empresa con id mas bajo (convencion:
     * SIGCON DEMO company_id=1 es la fuente autoritativa de config global).
     */
    @Query(value = "SELECT value FROM parameters WHERE name = :name "
                 + "AND deleted_at IS NULL ORDER BY company_id ASC LIMIT 1",
           nativeQuery = true)
    Optional<String> findGlobalValueByName(@org.springframework.data.repository.query.Param("name") String name);
}

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
}

package com.sigcon.backend.parametrization.parameters.domain.repository;

import com.sigcon.backend.parametrization.parameters.domain.model.Parameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParameterRepository extends JpaRepository<Parameter, Long> {
    Optional<Parameter> findByName(String name);
    List<Parameter> findByActiveTrue();
    Page<Parameter> findByActiveTrue(Pageable pageable);
    boolean existsByName(String name);
}

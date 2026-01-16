package com.sigcon.backend.parameters.domain.repository;

import com.sigcon.backend.parameters.domain.model.Parameter;
import com.sigcon.backend.parameters.domain.model.UserParameter;
import com.sigcon.backend.users.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserParameterRepository extends JpaRepository<UserParameter, Long> {
    List<UserParameter> findByUser(User user);
    Page<UserParameter> findByUser(User user, Pageable pageable);
    Optional<UserParameter> findByUserAndParameter(User user, Parameter parameter);
    boolean existsByUserAndParameter(User user, Parameter parameter);
    void deleteByUserAndParameter(User user, Parameter parameter);
}

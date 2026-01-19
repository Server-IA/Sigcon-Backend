package com.sigcon.backend.parametrization.users.domain.repository;


import com.sigcon.backend.parametrization.users.domain.model.BlackListedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlackListedTokenRepository extends JpaRepository<BlackListedToken, Long> {

    boolean existsByToken(String token);
    Optional<BlackListedToken> findByToken(String token);
}

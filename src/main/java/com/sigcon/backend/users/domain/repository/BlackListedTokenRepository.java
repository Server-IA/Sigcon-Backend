package com.sigcon.backend.users.domain.repository;


import com.sigcon.backend.users.domain.model.BlackListedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlackListedTokenRepository extends JpaRepository<BlackListedToken, Long> {

    boolean existsByToken(String token);
    Optional<BlackListedToken> findByToken(String token);
}

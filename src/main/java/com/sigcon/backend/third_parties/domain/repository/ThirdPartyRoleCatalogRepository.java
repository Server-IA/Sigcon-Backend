package com.sigcon.backend.third_parties.domain.repository;

import com.sigcon.backend.third_parties.domain.model.ThirdPartyRoleCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThirdPartyRoleCatalogRepository extends JpaRepository<ThirdPartyRoleCatalog, Long> {
    Optional<ThirdPartyRoleCatalog> findByNameIgnoreCase(String name);
}

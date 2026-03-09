package com.sigcon.backend.third_parties.domain.repository;

import com.sigcon.backend.third_parties.domain.model.ThirdPartyStatusCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThirdPartyStatusCatalogRepository extends JpaRepository<ThirdPartyStatusCatalog, Long> {
    Optional<ThirdPartyStatusCatalog> findByNameIgnoreCase(String name);
}

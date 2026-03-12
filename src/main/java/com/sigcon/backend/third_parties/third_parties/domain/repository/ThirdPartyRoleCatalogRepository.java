package com.sigcon.backend.third_parties.third_parties.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyRoleCatalog;

import java.util.Optional;

public interface ThirdPartyRoleCatalogRepository extends JpaRepository<ThirdPartyRoleCatalog, Long> {
    Optional<ThirdPartyRoleCatalog> findByNameIgnoreCase(String name);
}

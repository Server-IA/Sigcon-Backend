package com.sigcon.backend.third_parties.third_parties.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyStatusCatalog;

import java.util.Optional;

public interface ThirdPartyStatusCatalogRepository extends JpaRepository<ThirdPartyStatusCatalog, Long> {
    Optional<ThirdPartyStatusCatalog> findByNameIgnoreCase(String name);
}

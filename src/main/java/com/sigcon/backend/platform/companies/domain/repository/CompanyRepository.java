package com.sigcon.backend.platform.companies.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.model.Company.CompanyStatus;

/**
 * Acceso a empresas (tenants). Usado por {@code PLATFORM_ADMIN} para CRUD
 * y por el framework interno (TenantContext, ApiKeyFilter AAEF) para
 * resolver la empresa actual.
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long>,
        JpaSpecificationExecutor<Company> {

    Optional<Company> findByNitAndDeletedAtIsNull(String nit);

    boolean existsByNitAndDeletedAtIsNull(String nit);

    boolean existsByNitAndIdNotAndDeletedAtIsNull(String nit, Long id);

    /**
     * QA Bloque PA Bug 25 (HU-PA-10 E2, 2026-05-09): chequeo de unicidad de NIT
     * incluyendo empresas soft-deleted. Util para evitar reuso accidental de NIT
     * de una empresa "eliminada" que sigue persistida con deleted_at != null.
     */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT COUNT(*) > 0 FROM companies WHERE nit = :nit",
            nativeQuery = true)
    boolean existsByNit(@org.springframework.data.repository.query.Param("nit") String nit);

    List<Company> findByStatusAndDeletedAtIsNull(CompanyStatus status);
}

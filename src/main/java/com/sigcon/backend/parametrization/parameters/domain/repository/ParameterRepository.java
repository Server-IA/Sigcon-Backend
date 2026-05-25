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

    /**
     * QA Nomina (2026-05-25) ERR-NOM-001: duplicidad scoped por empresa.
     *
     * <p>El check anterior {@link #existsByNameAndCategoryAndIdNot} no incluia
     * company_id. Para un PLATFORM_ADMIN (que bypassa el tenant filter) veia las
     * copias del mismo parametro de TODAS las empresas (ej. 7 filas
     * sigcon.nomina.smlv, una por empresa) y rechazaba la edicion con un falso
     * "ya existe". Este metodo limita la verificacion a la MISMA empresa del
     * parametro editado, que es el comportamiento correcto en multi-tenant
     * (cada empresa tiene su propia copia del parametro).
     */
    boolean existsByNameAndCategoryAndCompanyIdAndIdNot(
            String name, CategoryParameter category, Long companyId, Long id);

    List<Parameter> findByCategoryAndDeletedAtIsNull(CategoryParameter category);
    Optional<Parameter> findByNameAndDeletedAtIsNull(String name);

    /**
     * Busca un parametro SIN aplicar el tenant filter. Usado para configuracion
     * global de plataforma (ej. AGROFUSION_API_KEY unica cross-empresa).
     *
     * <p>Devuelve el primero encontrado en la empresa con id mas bajo (convencion:
     * SIGCON DEMO company_id=1 es la fuente autoritativa de config global).
     */
    @Query(value = "SELECT value FROM parameters WHERE name = :name "
                 + "AND deleted_at IS NULL ORDER BY company_id ASC LIMIT 1",
           nativeQuery = true)
    Optional<String> findGlobalValueByName(@org.springframework.data.repository.query.Param("name") String name);

    /**
     * QA Bloque PA Bug 83 (HU-PA-PLAT-03 E1, 2026-05-11): busca un parametro
     * scoped por empresa SIN aplicar el tenant filter (necesario para que el
     * SessionInvalidationFilter pueda validar el JWT_INVALIDATION_CUTOFF de
     * empresas distintas a la del request actual).
     */
    @Query(value = "SELECT value FROM parameters WHERE name = :name "
                 + "AND company_id = :companyId AND deleted_at IS NULL LIMIT 1",
           nativeQuery = true)
    Optional<String> findValueByNameAndCompanyId(
            @org.springframework.data.repository.query.Param("name") String name,
            @org.springframework.data.repository.query.Param("companyId") Long companyId);
}

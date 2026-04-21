package com.sigcon.backend.parametrization.account_mappings.domain.repository;

import com.sigcon.backend.parametrization.account_mappings.domain.model.AccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para {@link AccountMapping}.
 *
 * <p>El soft delete es manejado por {@code @SQLDelete} en la entidad, por lo que
 * cualquier {@code find} automaticamente filtra {@code deleted_at IS NULL}.
 */
@Repository
public interface AccountMappingRepository extends JpaRepository<AccountMapping, Long> {

    /**
     * Busca un mapeo por codigo de concepto.
     *
     * @param conceptCode codigo logico del concepto (ej. AR_CLIENTES)
     * @return mapeo si existe y no esta eliminado
     */
    Optional<AccountMapping> findByConceptCode(String conceptCode);

    /**
     * Verifica existencia de un concepto (para validacion fail-fast al iniciar la app).
     *
     * @param conceptCode codigo logico del concepto
     * @return true si el mapeo existe y esta activo
     */
    boolean existsByConceptCode(String conceptCode);

    /**
     * Lista todos los mapeos ordenados por codigo de concepto (para vista admin).
     */
    List<AccountMapping> findAllByOrderByConceptCodeAsc();

    // ======== Multi-tenant variants (Bloque G fix) ========
    // Los metodos de arriba solo deben usarse con TenantContext activo que habilita
    // @Filter. Si el flujo corre sin tenant (startup, async, scheduler) usar los
    // metodos with-company explicitos abajo para evitar leaks cross-tenant.

    /**
     * Lookup explicito por (companyId, conceptCode). Usa nativeQuery para bypass
     * del {@code @Filter} de Hibernate — necesario porque el caller puede estar
     * en un tenant distinto (p.ej. un usuario de empresa A debe poder resolver
     * sus mapeos aunque el filter global apunte a otra empresa si algo falla).
     * Sigue respetando soft delete con WHERE deleted_at IS NULL.
     */
    @org.springframework.data.jpa.repository.Query(value =
        "SELECT * FROM account_mappings WHERE company_id = :companyId "
      + "AND concept_code = :conceptCode AND deleted_at IS NULL",
      nativeQuery = true)
    Optional<AccountMapping> findByCompanyIdAndConceptCodeAndDeletedAtIsNull(
            @org.springframework.data.repository.query.Param("companyId") Long companyId,
            @org.springframework.data.repository.query.Param("conceptCode") String conceptCode);

    /** Variante para fail-fast: existe el mapeo en la empresa dada? */
    @org.springframework.data.jpa.repository.Query(value =
        "SELECT EXISTS(SELECT 1 FROM account_mappings WHERE company_id = :companyId "
      + "AND concept_code = :conceptCode AND deleted_at IS NULL)",
      nativeQuery = true)
    boolean existsByCompanyIdAndConceptCodeAndDeletedAtIsNull(
            @org.springframework.data.repository.query.Param("companyId") Long companyId,
            @org.springframework.data.repository.query.Param("conceptCode") String conceptCode);
}

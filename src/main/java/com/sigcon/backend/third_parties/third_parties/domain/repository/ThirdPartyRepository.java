package com.sigcon.backend.third_parties.third_parties.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

import java.util.List;

public interface ThirdPartyRepository extends JpaRepository<ThirdParty, Long>, JpaSpecificationExecutor<ThirdParty> {
    boolean existsByNitAndDvAndDeletedAtIsNull(String nit, String dv);

    boolean existsByNitAndDvAndIdNotAndDeletedAtIsNull(String nit, String dv, Long id);

    boolean existsByNitAndDeletedAtIsNull(String nit);

    List<ThirdParty> findByNitAndDeletedAtIsNull(String nit);
    boolean existsByNitAndIdNotAndDeletedAtIsNull(String nit, Long id);

    /**
     * QA Integracion (2026-05-26): busqueda por codigo interno. Usado por
     * {@code ThirdPartyResolver} como fallback determinista para reencontrar
     * terceros auto-creados desde AAEF (codigo {@code AAEF-{nit}}) cuando la
     * busqueda por NIT no coincide (p.ej. el NIT quedo almacenado con ceros a
     * la izquierda). Evita el choque con {@code uk_third_parties_company_code_active}
     * al reprocesar el mismo cliente en lotes posteriores.
     */
    List<ThirdParty> findByThirdPartyCodeAndDeletedAtIsNull(String thirdPartyCode);

    /**
     * BUG-01 (TER-RF-02/07, 2026-06-02): consecutivo robusto del codigo
     * {@code TER{anio}######}. Devuelve el MAXIMO sufijo numerico de los
     * codigos del tenant que empiezan por el prefijo del anio (incluye
     * activos y eliminados, para NO reutilizar codigos). El consecutivo se
     * calcula como MAX+1, reemplazando el antipatron {@code count()+1} que
     * colisionaba con {@code uk_third_parties_company_code_active} cuando
     * habia huecos por soft-delete o prefijos mixtos del seed (CLI-*, PROV-*).
     *
     * <p>Query nativo: el {@code company_id} va explicito porque los queries
     * nativos NO heredan el {@code @Filter("tenantFilter")}. El prefijo
     * "TER{anio}" tiene 7 caracteres, por eso {@code SUBSTRING(... FROM 8)}
     * extrae el sufijo de 6 digitos.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(third_party_code FROM 8) AS INTEGER)), 0) "
            + "FROM third_parties WHERE company_id = :companyId AND third_party_code LIKE :prefix",
            nativeQuery = true)
    Integer findMaxThirdPartyCodeSequence(@Param("companyId") Long companyId, @Param("prefix") String prefix);

    /**
     * PT-09 (TER-RF-02/03, 2026-06-02): cuenta terceros activos del tenant cuya
     * razon social NORMALIZADA (minusculas, sin espacios sobrantes) coincide con
     * la indicada, opcionalmente excluyendo un id (para la edicion). Query nativo:
     * {@code company_id} explicito (no hereda el @Filter de tenant). La
     * normalizacion en SQL replica trim + colapso de espacios multiples.
     */
    @Query(value = "SELECT COUNT(*) FROM third_parties "
            + "WHERE company_id = :companyId AND deleted_at IS NULL "
            + "AND (:excludeId IS NULL OR id <> :excludeId) "
            + "AND regexp_replace(lower(btrim(business_name)), '\\s+', ' ', 'g') = :normalizedName",
            nativeQuery = true)
    long countActiveByNormalizedBusinessName(@Param("companyId") Long companyId,
            @Param("normalizedName") String normalizedName, @Param("excludeId") Long excludeId);

    /**
     * PT-04 (TER-RF-10, 2026-06-02): lista los terceros dados de baja
     * (deleted_at != null) del tenant. Query nativo porque la entidad tiene
     * {@code @Where(deleted_at IS NULL)} y JPA jamas devuelve filas eliminadas.
     * Columnas: id, third_party_code, nit, dv, business_name, deleted_at.
     */
    @Query(value = "SELECT id, third_party_code, nit, dv, business_name, deleted_at "
            + "FROM third_parties WHERE company_id = :companyId AND deleted_at IS NOT NULL "
            + "ORDER BY deleted_at DESC", nativeQuery = true)
    List<Object[]> findDeletedByCompany(@Param("companyId") Long companyId);

    /**
     * PT-04: NIT de un tercero eliminado del tenant (para validar duplicidad
     * antes de reactivar). Devuelve null si no existe / no esta eliminado.
     */
    @Query(value = "SELECT nit FROM third_parties "
            + "WHERE id = :id AND company_id = :companyId AND deleted_at IS NOT NULL", nativeQuery = true)
    String findDeletedNitById(@Param("id") Long id, @Param("companyId") Long companyId);

    /**
     * PT-04: reactiva (limpia deleted_at) un tercero eliminado del tenant.
     * Devuelve el numero de filas afectadas (1 si se reactivo).
     */
    @Modifying
    @Query(value = "UPDATE third_parties SET deleted_at = NULL, updated_at = NOW() "
            + "WHERE id = :id AND company_id = :companyId AND deleted_at IS NOT NULL", nativeQuery = true)
    int reactivateById(@Param("id") Long id, @Param("companyId") Long companyId);
}

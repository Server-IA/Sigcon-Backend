package com.sigcon.backend.third_parties.third_parties.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}

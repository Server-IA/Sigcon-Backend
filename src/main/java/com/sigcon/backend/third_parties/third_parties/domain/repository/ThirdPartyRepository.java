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
}

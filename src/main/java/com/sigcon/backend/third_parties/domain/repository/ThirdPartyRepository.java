package com.sigcon.backend.third_parties.domain.repository;

import com.sigcon.backend.third_parties.domain.model.ThirdParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ThirdPartyRepository extends JpaRepository<ThirdParty, Long>, JpaSpecificationExecutor<ThirdParty> {
    boolean existsByNitAndDvAndDeletedAtIsNull(String nit, String dv);
    boolean existsByNitAndDvAndIdNotAndDeletedAtIsNull(String nit, String dv, Long id);
    boolean existsByNitAndDeletedAtIsNull(String nit);
    List<ThirdParty> findByNitAndDeletedAtIsNull(String nit);
}

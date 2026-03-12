package com.sigcon.backend.third_parties.commercial_data.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.third_parties.commercial_data.domain.model.CommercialData;

@Repository
public interface CommercialDataRepository extends JpaRepository<CommercialData, Long>, 
    JpaSpecificationExecutor<CommercialData> { 

        //buscar los datos comerciales de un terceros 
        Optional<CommercialData> findByThirdPartyIdAndDeletedAtIsNull(Long thirdPartyId);

        //verificar si ya existe un registro vigente para un tercero 
        boolean existsByThirdPartyIdAndDeletedAtIsNull(Long thirdPartyId);
}

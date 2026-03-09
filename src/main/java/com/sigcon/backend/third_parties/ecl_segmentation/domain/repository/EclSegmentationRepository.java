package com.sigcon.backend.third_parties.ecl_segmentation.domain.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.EclSegmentation;

import org.springframework.stereotype.Repository;

@Repository
public interface EclSegmentationRepository extends JpaRepository<EclSegmentation,Long>, 
  JpaSpecificationExecutor<EclSegmentation> {

    //Buscar segmentacion vigente de un cliente 
    Optional<EclSegmentation> findByClientIdAndDeletedAtIsNull(Long clientId);

    //verificar si existe segmentacion para un cliente 
    boolean existsByClientIdAndDeletedAtIsNull(Long clientId);

}
